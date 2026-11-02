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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    private final ExecutorService pool;
    private final Map<TierList, Long> resting = new EnumMap<>(TierList.class);

    /** A list's own complaint, which counts against it like the server's circuit breaker. */
    private static final class ListFailure extends Exception {
        ListFailure(String reason) {
            super(reason, null, false, false);
        }
    }

    public DirectLists(Transport transport, LongSupplier clock) {
        this.transport = transport;
        this.clock = clock;
        this.pool = Executors.newFixedThreadPool(TierList.values().length, runnable -> {
            Thread thread = new Thread(runnable, "TierProfiles list lookups");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void stop() {
        pool.shutdownNow();
    }

    public static String canonicalKit(String raw) {
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return ALIASES.getOrDefault(key, key);
    }

    /** Looks every player up on every list, all lists at once, and returns the server's reply shape. */
    public String lookup(Map<UUID, String> players) {
        JsonArray profiles = new JsonArray();
        for (Map.Entry<UUID, String> player : players.entrySet()) {
            Map<TierList, Future<JsonObject>> calls = new EnumMap<>(TierList.class);
            for (TierList list : TierList.values()) {
                calls.put(list, pool.submit(() -> call(list, player.getKey(), player.getValue())));
            }
            JsonObject lists = new JsonObject();
            for (Map.Entry<TierList, Future<JsonObject>> call : calls.entrySet()) {
                lists.add(call.getKey().id(), await(call.getValue()));
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

    private static JsonObject await(Future<JsonObject> call) {
        try {
            return call.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unavailable("interrupted");
        } catch (Exception e) {
            return unavailable("network");
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
            String body;
            try {
                body = transport.send("GET", url, null, null);
            } catch (Transport.StatusException e) {
                if (e.status() == 404) {
                    return unranked();
                }
                throw new ListFailure(e.status() == 429 ? "rate_limited" : "http_" + e.status());
            }
            return parse(list, body, uuid);
        } catch (ListFailure e) {
            rest(list);
            return unavailable(e.getMessage());
        } catch (IOException e) {
            rest(list);
            return unavailable("network");
        }
    }

    private void rest(TierList list) {
        synchronized (resting) {
            resting.put(list, clock.getAsLong() + COOLDOWN_MILLIS);
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
