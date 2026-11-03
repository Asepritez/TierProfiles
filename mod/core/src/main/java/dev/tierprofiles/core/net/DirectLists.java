package dev.tierprofiles.core.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.tierprofiles.core.model.TierList;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Asks each list's own website, with no TierProfiles server in between. It answers in
 * exactly the shape the server would, so ProfileService handles both the same way.
 * This is the same work as server/src/providers, and the two should change together.
 *
 * Only UUIDs and names are sent, and only to the five list sites.
 */
public final class DirectLists {
    /** A list that fails is left alone this long, so a site that's down isn't hammered. */
    static final long COOLDOWN_MILLIS = 60 * 1000;
    static final int STRIKES_TO_REST = 3;
    private static final int ATTEMPTS = 2;
    private static final long RETRY_PAUSE_MILLIS = 400;
    private static final int MCPVP_MIN_QUERY = 3;
    private static final int MCPVP_RESULT_CAP = 50;
    private static final int PVPHQ_NO_POSITION = 99999;
    private static final Pattern MCPVP_RANK = Pattern.compile("(HT|MT|LT|ST)[1-6]");
    private static final Pattern PVPHQ_RANK = Pattern.compile("(HT|MT|LT)[1-5]");

    /** Lists disagree on kit names, so they're settled on one id, as kits.ts does. */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("vanilla", "crystal"),
            Map.entry("nethop", "neth_pot"),
            Map.entry("netherite_pot", "neth_pot"),
            Map.entry("cart", "minecart"),
            Map.entry("diamond_smp", "dia_smp"),
            Map.entry("spear_mace", "spear"),
            Map.entry("spearmace", "spear"),
            Map.entry("ht_cart", "minecart"),
            Map.entry("carts", "minecart"),
            Map.entry("diamond_pot", "pot"),
            Map.entry("netherite_op", "neth_pot"),
            Map.entry("netherite_smp", "smp"));

    private final Transport transport;
    private final LongSupplier clock;
    private final Map<TierList, ThreadPoolExecutor> pools = new EnumMap<>(TierList.class);
    private final AtomicLong order = new AtomicLong();
    private final Map<TierList, Long> resting = new EnumMap<>(TierList.class);
    // Guarded by `resting`. Failures in a row for each list.
    private final Map<TierList, Integer> strikes = new EnumMap<>(TierList.class);

    /**
     * Hears each list's answer the moment it arrives, so a fast list never waits for a
     * slow one. Called from lookup threads.
     */
    public interface Listener {
        void answered(UUID uuid, TierList list, JsonObject result);

        /** Every list has now answered for this player, one way or another. */
        void finished(UUID uuid);
    }

    /**
     * How many calls each list may have going at once. Enough to clear a full lobby in
     * a few seconds, few enough to stay polite. McPvP's search and PVPHQ are heavier.
     */
    static int concurrency(TierList list) {
        return switch (list) {
            case MCTIERS -> 6;
            case PVPTIERS, SUBTIERS -> 4;
            case MCPVP -> 3;
            case PVPHQ -> 2;
        };
    }

    /** One call waiting for its list. Urgent ones, like a card just opened, go first. */
    private static final class Job implements Runnable, Comparable<Job> {
        private final boolean urgent;
        private final long order;
        private final UUID player;
        private final Runnable work;

        Job(boolean urgent, long order, UUID player, Runnable work) {
            this.urgent = urgent;
            this.order = order;
            this.player = player;
            this.work = work;
        }

        @Override
        public void run() {
            work.run();
        }

        @Override
        public int compareTo(Job other) {
            if (urgent != other.urgent) {
                return urgent ? -1 : 1;
            }
            return Long.compare(order, other.order);
        }
    }

    /** A list's own complaint, which counts against it like the server's circuit breaker. */
    private static final class ListFailure extends Exception {
        ListFailure(String reason) {
            super(reason, null, false, false);
        }
    }

    public DirectLists(Transport transport, LongSupplier clock) {
        this.transport = transport;
        this.clock = clock;
        for (TierList list : TierList.values()) {
            int threads = concurrency(list);
            // Each list has its own queue and threads, which end when there's nothing to do.
            ThreadPoolExecutor pool = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, new PriorityBlockingQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "TierProfiles " + list.displayName() + " lookups");
                thread.setDaemon(true);
                return thread;
            });
            pool.allowCoreThreadTimeOut(true);
            pools.put(list, pool);
        }
    }

    public void stop() {
        pools.values().forEach(ThreadPoolExecutor::shutdownNow);
    }

    private void run(TierList list, boolean urgent, Runnable work) {
        run(list, urgent, null, work);
    }

    private void run(TierList list, boolean urgent, UUID player, Runnable work) {
        pools.get(list).execute(new Job(urgent, order.incrementAndGet(), player, work));
    }

    /**
     * Moves a player who is still waiting in any list's queue to the front, for when you
     * pick them in Tab before their turn came. Calls already running carry on as they are.
     * Returns how many waiting calls moved.
     */
    public int hurry(UUID player) {
        int moved = 0;
        for (ThreadPoolExecutor pool : pools.values()) {
            for (Runnable waiting : pool.getQueue().toArray(new Runnable[0])) {
                if (waiting instanceof Job job && !job.urgent && player.equals(job.player) && pool.getQueue().remove(job)) {
                    pool.execute(new Job(true, order.incrementAndGet(), player, job.work));
                    moved++;
                }
            }
        }
        return moved;
    }

    public static String canonicalKit(String raw) {
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return ALIASES.getOrDefault(key, key);
    }

    /**
     * Starts looking one player up on every list and returns at once. Each list works
     * through its own queue, so the listener hears from fast lists straight away.
     */
    public void lookupEach(UUID uuid, String name, boolean urgent, Listener listener) {
        AtomicInteger left = new AtomicInteger(TierList.values().length);
        for (TierList list : TierList.values()) {
            run(list, urgent, uuid, () -> {
                JsonObject result;
                try {
                    result = call(list, uuid, name);
                } catch (RuntimeException e) {
                    result = unavailable("error");
                }
                try {
                    listener.answered(uuid, list, result);
                } finally {
                    if (left.decrementAndGet() == 0) {
                        listener.finished(uuid);
                    }
                }
            });
        }
    }

    /** Asks one list again, for a list that couldn't answer the first time. */
    public void lookupOne(UUID uuid, String name, TierList list, Listener listener) {
        run(list, false, () -> {
            JsonObject result;
            try {
                result = call(list, uuid, name);
            } catch (RuntimeException e) {
                result = unavailable("error");
            }
            try {
                listener.answered(uuid, list, result);
            } finally {
                listener.finished(uuid);
            }
        });
    }

    /** Looks every player up on every list and waits, returning the server's reply shape. */
    public String lookup(Map<UUID, String> players) {
        Map<UUID, Map<TierList, JsonObject>> answers = new ConcurrentHashMap<>();
        CountDownLatch done = new CountDownLatch(players.size());
        Listener collect = new Listener() {
            @Override
            public void answered(UUID uuid, TierList list, JsonObject result) {
                answers.computeIfAbsent(uuid, key -> new ConcurrentHashMap<>()).put(list, result);
            }

            @Override
            public void finished(UUID uuid) {
                done.countDown();
            }
        };
        players.forEach((uuid, name) -> lookupEach(uuid, name, false, collect));
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        JsonArray profiles = new JsonArray();
        for (Map.Entry<UUID, String> player : players.entrySet()) {
            Map<TierList, JsonObject> found = answers.getOrDefault(player.getKey(), Map.of());
            JsonObject lists = new JsonObject();
            for (TierList list : TierList.values()) {
                lists.add(list.id(), found.getOrDefault(list, unavailable("interrupted")));
            }
            JsonObject profile = new JsonObject();
            profile.addProperty("uuid", player.getKey().toString());
            profile.addProperty("name", player.getValue());
            profile.add("lists", lists);
            profiles.add(profile);
        }
        JsonObject reply = new JsonObject();
        reply.add("profiles", profiles);
        return reply.toString();
    }

    /**
     * Finds the real player behind a name, using McPvP's search, which already gets
     * names. For tab rows a server fills with real players' names under made-up IDs,
     * such as a leaderboard. Hands back null when McPvP doesn't know that exact name.
     */
    public void resolveName(String name, Consumer<UUID> found) {
        run(TierList.MCPVP, false, () -> {
            UUID uuid = null;
            try {
                uuid = findOnMcPvp(name);
            } catch (IOException | RuntimeException e) {
                // Not knowing is fine: the row just stays untagged.
            }
            found.accept(uuid);
        });
    }

    private UUID findOnMcPvp(String name) throws IOException {
        if (name == null || name.length() < MCPVP_MIN_QUERY) {
            return null;
        }
        String body = transport.send("GET", "https://www.mcpvp.com/tiers/search?q=" + URLEncoder.encode(name, StandardCharsets.UTF_8), null, null);
        JsonElement root = JsonParser.parseString(body);
        if (!(root instanceof JsonObject data) || !(data.get("players") instanceof JsonArray players)) {
            return null;
        }
        for (JsonElement player : players) {
            if (player instanceof JsonObject candidate && name.equalsIgnoreCase(string(candidate.get("name")))) {
                return uuidOf(string(candidate.get("uuid")));
            }
        }
        return null;
    }

    private static UUID uuidOf(String text) {
        if (text == null) {
            return null;
        }
        String plain = text.replace("-", "");
        if (plain.length() != 32) {
            return null;
        }
        try {
            return new UUID(Long.parseUnsignedLong(plain.substring(0, 16), 16), Long.parseUnsignedLong(plain.substring(16), 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private JsonObject call(TierList list, UUID uuid, String name) {
        synchronized (resting) {
            Long until = resting.get(list);
            if (until != null && clock.getAsLong() < until) {
                return unavailable("circuit_open");
            }
        }
        String url = url(list, uuid, name);
        if (url == null) {
            return unavailable(name == null ? "name_required" : "name_too_short");
        }
        try {
            JsonObject result = parse(list, fetch(url), uuid);
            synchronized (resting) {
                strikes.remove(list);
            }
            return result;
        } catch (Unranked e) {
            return unranked();
        } catch (ListFailure e) {
            strike(list);
            return unavailable(e.getMessage());
        } catch (IOException e) {
            strike(list);
            return unavailable("network");
        }
    }

    /** A 404 from a list means it doesn't know the player. */
    private static final class Unranked extends Exception {
        Unranked() {
            super(null, null, false, false);
        }
    }

    /**
     * One call, tried a second time after a short pause if the list hiccups. Some lists
     * answer a burst with the odd 503 that works a moment later.
     */
    private String fetch(String url) throws IOException, ListFailure, Unranked {
        for (int attempt = 1; ; attempt++) {
            try {
                return transport.send("GET", url, null, null);
            } catch (Transport.StatusException e) {
                if (e.status() == 404) {
                    throw new Unranked();
                }
                boolean passing = e.status() >= 500 || e.status() == 429;
                if (!passing || attempt >= ATTEMPTS) {
                    throw new ListFailure(e.status() == 429 ? "rate_limited" : "http_" + e.status());
                }
            } catch (IOException e) {
                if (attempt >= ATTEMPTS) {
                    throw e;
                }
            }
            try {
                Thread.sleep(RETRY_PAUSE_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", e);
            }
        }
    }

    /** A list is only rested after failing several times running, not for one bad moment. */
    private void strike(TierList list) {
        synchronized (resting) {
            int count = strikes.merge(list, 1, Integer::sum);
            if (count >= STRIKES_TO_REST) {
                resting.put(list, clock.getAsLong() + COOLDOWN_MILLIS);
                strikes.remove(list);
            }
        }
    }

    static String url(TierList list, UUID uuid, String name) {
        String plain = uuid.toString().replace("-", "");
        return switch (list) {
            case MCTIERS -> "https://mctiers.gg/api/profile/" + uuid;
            // A loose name search, so the UUID decides which result is really them.
            case MCPVP -> name == null || name.length() < MCPVP_MIN_QUERY ? null
                    : "https://www.mcpvp.com/tiers/search?q=" + URLEncoder.encode(name, StandardCharsets.UTF_8);
            // This one answers 400 to a dashed UUID.
            case PVPTIERS -> "https://pvptiers.com/api/profile/" + plain;
            case SUBTIERS -> "https://subtiers.net/api/v2/profile/" + uuid;
            case PVPHQ -> "https://pvphq.com/api/ranked/" + uuid;
        };
    }

    static JsonObject parse(TierList list, String body, UUID uuid) throws ListFailure {
        JsonElement root;
        try {
            root = body == null || body.isBlank() ? null : JsonParser.parseString(body);
        } catch (JsonParseException e) {
            throw new ListFailure("bad_json");
        }
        return switch (list) {
            case MCTIERS, PVPTIERS, SUBTIERS -> parseRankings(root);
            case MCPVP -> parseMcPvp(root, uuid);
            case PVPHQ -> parsePvpHq(root);
        };
    }

    /**
     * MCTiers, PvPTiers and SubTiers share a format:
     * {region, points, overall, rankings: {sword: {tier, pos, peak_tier, peak_pos, attained, retired}}}.
     * pos 0 is High and 1 is Low, so tier 3 pos 1 is LT3.
     */
    private static JsonObject parseRankings(JsonElement root) throws ListFailure {
        if (!(root instanceof JsonObject data) || !(data.get("rankings") instanceof JsonObject rankings)) {
            throw new ListFailure("bad_shape");
        }
        JsonArray tiers = new JsonArray();
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, JsonElement> kitEntry : rankings.entrySet()) {
            if (!(kitEntry.getValue() instanceof JsonObject value)) {
                continue;
            }
            String rank = rankLabel(value.get("tier"), value.get("pos"));
            String kit = canonicalKit(kitEntry.getKey());
            // MCTiers sends both "nethop" and "neth_pot", which become one kit here.
            if (rank == null || !seen.add(kit)) {
                continue;
            }
            tiers.add(entry(kit, rank, rankLabel(value.get("peak_tier"), value.get("peak_pos")), raw(value.get("attained")), isTrue(value.get("retired"))));
        }
        if (tiers.isEmpty()) {
            return unranked();
        }
        return ok(string(data.get("region")), null, raw(data.get("points")), raw(data.get("overall")), tiers);
    }

    private static JsonObject parseMcPvp(JsonElement root, UUID uuid) throws ListFailure {
        if (!(root instanceof JsonObject data) || !(data.get("players") instanceof JsonArray players)) {
            throw new ListFailure("bad_shape");
        }
        String wanted = uuid.toString().replace("-", "");
        JsonObject match = null;
        for (JsonElement player : players) {
            if (player instanceof JsonObject candidate && wanted.equals(string(candidate.get("uuid")))) {
                match = candidate;
                break;
            }
        }
        if (match == null) {
            // A full page sorted by points may have pushed them off the end, so we can't say.
            return players.size() >= MCPVP_RESULT_CAP ? unavailable("search_truncated") : unranked();
        }
        if (!(match.get("kitRanks") instanceof JsonObject ranks)) {
            throw new ListFailure("bad_shape");
        }
        JsonObject peaks = match.get("kitPeakRanks") instanceof JsonObject object ? object : new JsonObject();
        JsonObject retired = match.get("kitRetired") instanceof JsonObject object ? object : new JsonObject();
        JsonArray tiers = new JsonArray();
        for (Map.Entry<String, JsonElement> kitEntry : ranks.entrySet()) {
            String rank = matching(MCPVP_RANK, kitEntry.getValue());
            if (rank != null) {
                tiers.add(entry(canonicalKit(kitEntry.getKey()), rank, matching(MCPVP_RANK, peaks.get(kitEntry.getKey())), null, isTrue(retired.get(kitEntry.getKey()))));
            }
        }
        if (tiers.isEmpty()) {
            return unranked();
        }
        return ok(string(match.get("region")), string(match.get("country")), raw(match.get("points")), raw(match.get("rank")), tiers);
    }

    /**
     * {data: {SWORD: {rating, peakRating, gamesPlayed, grantedTier}}, globalPosition}. Players it
     * doesn't know get a 200 with empty data, and positions count from zero.
     */
    private static JsonObject parsePvpHq(JsonElement root) throws ListFailure {
        if (root == null || root.isJsonNull()) {
            return unranked();
        }
        if (!(root instanceof JsonObject object)) {
            throw new ListFailure("bad_shape");
        }
        if (!(object.get("data") instanceof JsonObject data)) {
            return unranked();
        }
        JsonArray tiers = new JsonArray();
        for (Map.Entry<String, JsonElement> kitEntry : data.entrySet()) {
            if (!(kitEntry.getValue() instanceof JsonObject value)) {
                continue;
            }
            String rank = pvphqRank(string(value.get("grantedTier")));
            if (rank != null) {
                JsonObject entry = entry(canonicalKit(kitEntry.getKey()), rank, null, null, false);
                Number rating = raw(value.get("rating"));
                Number games = raw(value.get("gamesPlayed"));
                if (rating != null) {
                    entry.addProperty("rating", rating);
                }
                if (games != null) {
                    entry.addProperty("games", games);
                }
                tiers.add(entry);
            }
        }
        if (tiers.isEmpty()) {
            return unranked();
        }
        Double position = number(object.get("globalPosition"));
        Number overall = position == null || position < 0 || position >= PVPHQ_NO_POSITION ? null : position.longValue() + 1;
        return ok(null, null, null, overall, tiers);
    }

    /** HQTiers accepts both "HT3" and "High Tier 3", so we do too. */
    static String pvphqRank(String raw) {
        if (raw == null) {
            return null;
        }
        String rank = raw.trim().toUpperCase(Locale.ROOT)
                .replaceFirst("^HIGH[_ -]?TIER[_ -]?", "HT")
                .replaceFirst("^MID[_ -]?TIER[_ -]?", "MT")
                .replaceFirst("^LOW[_ -]?TIER[_ -]?", "LT")
                .replaceAll("[_ -]", "");
        return PVPHQ_RANK.matcher(rank).matches() ? rank : null;
    }

    private static String rankLabel(JsonElement tier, JsonElement pos) {
        Double level = number(tier);
        Double high = number(pos);
        if (level == null || high == null || level != Math.floor(level) || level < 1 || level > 5 || (high != 0 && high != 1)) {
            return null;
        }
        return (high == 0 ? "HT" : "LT") + level.intValue();
    }

    private static String matching(Pattern pattern, JsonElement value) {
        String text = string(value);
        return text != null && pattern.matcher(text).matches() ? text : null;
    }

    private static JsonObject entry(String kit, String rank, String peak, Number attained, boolean retired) {
        JsonObject entry = new JsonObject();
        entry.addProperty("kit", kit);
        entry.addProperty("rank", rank);
        entry.addProperty("peak", peak);
        entry.addProperty("attained", attained);
        entry.addProperty("retired", retired);
        return entry;
    }

    private static JsonObject ok(String region, String country, Number points, Number overall, JsonArray tiers) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "ok");
        result.addProperty("region", region);
        result.addProperty("country", country);
        result.addProperty("points", points);
        result.addProperty("overall", overall);
        result.add("tiers", tiers);
        return result;
    }

    private static JsonObject unranked() {
        JsonObject result = new JsonObject();
        result.addProperty("status", "unranked");
        return result;
    }

    private static JsonObject unavailable(String reason) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "unavailable");
        result.addProperty("reason", reason);
        return result;
    }

    private static Double number(JsonElement value) {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            double number = value.getAsDouble();
            return Double.isFinite(number) ? number : null;
        }
        return null;
    }

    /** A number exactly as the list wrote it, so a timestamp stays a whole number. */
    private static Number raw(JsonElement value) {
        return number(value) == null ? null : value.getAsNumber();
    }

    private static String string(JsonElement value) {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString();
            return text.isEmpty() ? null : text;
        }
        return null;
    }

    private static boolean isTrue(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }

    /** For tests: the kits a reply lists for one player on one list. */
    static List<String> kits(JsonObject listResult) {
        List<String> kits = new ArrayList<>();
        if (listResult.get("tiers") instanceof JsonArray tiers) {
            for (JsonElement tier : tiers) {
                kits.add(tier.getAsJsonObject().get("kit").getAsString());
            }
        }
        return kits;
    }
}
