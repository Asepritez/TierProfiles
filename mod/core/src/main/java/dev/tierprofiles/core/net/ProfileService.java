package dev.tierprofiles.core.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.tierprofiles.core.model.ListProfile;
import dev.tierprofiles.core.model.ListStatus;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.model.ProfileParser;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.Global;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Finds out players' tiers without ever making the game wait.
 *
 * The game thread only calls `request` and `profile`, which touch memory and return
 * at once. A background thread hands queued players to the lists, and each answer
 * lands in the cache as it arrives. Answers are also kept on disk, so after a server
 * switch or a restart, players you've seen before show their tiers straight away
 * while they're checked again. If anything goes wrong, the only effect is that a tag
 * appears later.
 */
public final class ProfileService {
    public static final int BATCH_SIZE = 8;
    /** Players handed to the lists per pump. Each list then works through them at its own pace. */
    static final int DIRECT_PER_PUMP = 64;

    static final long FRESH_MILLIS = 10 * 60 * 1000;
    static final long RETRY_BUSY_MILLIS = 30 * 1000;
    static final long RETRY_FAILED_MILLIS = 2 * 60 * 1000;
    static final long FIRST_BACKOFF_MILLIS = 5 * 1000;
    static final long MAX_BACKOFF_MILLIS = 5 * 60 * 1000;
    static final int MAX_CACHED = 4096;
    static final int MAX_QUEUED = 1024;
    /** Saved answers older than this aren't shown at all. Tiers change, but not that fast. */
    static final long DISK_MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000;
    static final long SAVE_EVERY_MILLIS = 30 * 1000;
    static final int MAX_NAME_LOOKUPS = 512;
    /** A list that was resting is asked again on its own once it's had time to recover. */
    static final long LIST_RETRY_MILLIS = DirectLists.COOLDOWN_MILLIS + 5 * 1000;

    private static final long PUMP_INTERVAL_MILLIS = 250;
    private static final int DISK_VERSION = 1;
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    /**
     * @param raw       the same answer in the server's shape, which is what goes to disk
     * @param refreshAt when to ask again
     * @param checkedAt when the lists last answered
     */
    private record Cached(PlayerProfile profile, JsonObject raw, long refreshAt, long checkedAt) {
    }

    private final Transport transport;
    private final DirectLists direct;
    private final Supplier<Global> global;
    private final LongSupplier clock;
    private final Path diskFile;

    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    /** Made-up tab IDs mapped to the real player whose name they show. Empty means no match. */
    private final Map<UUID, Optional<UUID>> aliases = new ConcurrentHashMap<>();

    // Guarded by `this`. The maps keep names to send, in the order they were asked for.
    private final Map<UUID, String> urgent = new LinkedHashMap<>();
    private final Map<UUID, String> queued = new LinkedHashMap<>();

    private volatile String lastStatus = "nothing sent yet";
    private volatile boolean dirty;
    private long lastSave;
    private long backoffUntil;
    private int failures;
    private ScheduledExecutorService executor;

    public ProfileService(Transport transport, Supplier<Global> global, LongSupplier clock) {
        this(transport, global, clock, null);
    }

    /** @param diskFile where answers are kept between sessions, or null to keep nothing */
    public ProfileService(Transport transport, Supplier<Global> global, LongSupplier clock, Path diskFile) {
        this.transport = transport;
        this.direct = new DirectLists(transport, clock);
        this.global = global;
        this.clock = clock;
        this.diskFile = diskFile;
    }

    /** Real accounts are version 4. NPCs and fake tab entries generally aren't. */
    public static boolean isLookupEligible(UUID uuid) {
        return uuid != null && uuid.version() == 4;
    }

    /** Only true once the player has seen the privacy notice and left lookups on. */
    public static boolean isAllowed(Global global) {
        return global.lookupsEnabled && global.noticeSeen;
    }

    /** What we know right now, or null. Safe on the render thread: it never waits. */
    public PlayerProfile profile(UUID uuid) {
        Cached cached = cache.get(uuid);
        if (cached == null) {
            Optional<UUID> real = aliases.get(uuid);
            cached = real == null || real.isEmpty() ? null : cache.get(real.get());
        }
        return cached == null ? null : cached.profile();
    }

    /** Asks for a player soon, unless what we hold is still fresh. Never blocks. */
    public void request(UUID uuid, String name) {
        enqueue(uuid, name, false);
    }

    /** Same, ahead of the queue. For a card someone has just opened. */
    public void requestUrgent(UUID uuid, String name) {
        enqueue(uuid, name, true);
    }

    /**
     * For a tab row with a made-up ID, such as a server's leaderboard of top players.
     * Tries each name that could be the player behind it, and once one matches a real
     * player, the row gets that player's tiers.
     */
    public void requestByName(UUID fake, List<String> names) {
        Optional<UUID> known = aliases.get(fake);
        if (known != null) {
            known.ifPresent(real -> request(real, null));
            return;
        }
        if (fake == null || !isAllowed(global.get()) || global.get().usesServer() || aliases.size() >= MAX_NAME_LOOKUPS) {
            return;
        }
        List<String> candidates = new ArrayList<>();
        for (String name : names) {
            if (name != null && PLAYER_NAME.matcher(name).matches() && !candidates.contains(name)) {
                candidates.add(name);
            }
        }
        if (candidates.isEmpty()) {
            aliases.put(fake, Optional.empty());
            return;
        }
        aliases.put(fake, Optional.empty());
        tryName(fake, candidates, 0);
    }

    private void tryName(UUID fake, List<String> candidates, int index) {
        if (index >= candidates.size()) {
            return;
        }
        String name = candidates.get(index);
        direct.resolveName(name, real -> {
            if (real == null) {
                tryName(fake, candidates, index + 1);
                return;
            }
            aliases.put(fake, Optional.of(real));
            request(real, name);
        });
    }

    private void enqueue(UUID uuid, String name, boolean first) {
        if (!isLookupEligible(uuid) || !isAllowed(global.get()) || inFlight.contains(uuid)) {
            return;
        }
        Cached cached = cache.get(uuid);
        if (cached != null && cached.refreshAt() > clock.getAsLong()) {
            return;
        }
        if (name == null && cached != null) {
            name = cached.profile().name();
        }
        synchronized (this) {
            if (first) {
                queued.remove(uuid);
                urgent.put(uuid, name);
            } else if (!urgent.containsKey(uuid) && queued.size() < MAX_QUEUED) {
                queued.put(uuid, name);
            }
        }
    }

    public synchronized int queueSize() {
        return urgent.size() + queued.size() + inFlight.size();
    }

    /** What the lists have said lately, for the Diagnostics page. */
    public static final class ListHealth {
        private final Map<ListStatus, Integer> counts = new EnumMap<>(ListStatus.class);
        private String lastReason;

        public int count(ListStatus status) {
            return counts.getOrDefault(status, 0);
        }

        public String lastReason() {
            return lastReason;
        }

        private void add(ListProfile profile) {
            counts.merge(profile.status(), 1, Integer::sum);
            if (profile.status() == ListStatus.UNAVAILABLE && profile.reason() != null) {
                lastReason = profile.reason();
            }
        }
    }

    public Map<TierList, ListHealth> health() {
        Map<TierList, ListHealth> health = new EnumMap<>(TierList.class);
        for (TierList list : TierList.values()) {
            health.put(list, new ListHealth());
        }
        for (Cached cached : cache.values()) {
            for (TierList list : TierList.values()) {
                health.get(list).add(cached.profile().list(list));
            }
        }
        return health;
    }

    public int knownPlayers() {
        return cache.size();
    }

    /** A short sentence about the last call to our server. */
    public String lastStatus() {
        return lastStatus;
    }

    /** Forget everything, here and on disk, for example after turning lookups off. */
    public void clear() {
        cache.clear();
        aliases.clear();
        synchronized (this) {
            urgent.clear();
            queued.clear();
            backoffUntil = 0;
            failures = 0;
        }
        dirty = true;
        saveSafely();
    }

    public void start() {
        load();
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "TierProfiles lookups");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::pumpSafely, PUMP_INTERVAL_MILLIS, PUMP_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
        direct.stop();
        saveSafely();
    }

    private void pumpSafely() {
        try {
            pump();
            if (dirty && clock.getAsLong() - lastSave >= SAVE_EVERY_MILLIS) {
                saveSafely();
            }
        } catch (RuntimeException e) {
            // One bad reply must not end the repeating task, or lookups stop for good.
        }
    }

    /** Sends what's waiting, if anything. Package-private so tests can step it. */
    void pump() {
        Global settings = global.get();
        if (!isAllowed(settings)) {
            synchronized (this) {
                urgent.clear();
                queued.clear();
            }
            return;
        }

        if (!settings.usesServer()) {
            pumpDirect();
            return;
        }

        long now = clock.getAsLong();
        Map<UUID, String> batch;
        synchronized (this) {
            if (now < backoffUntil) {
                return;
            }
            batch = new LinkedHashMap<>();
            moveInto(batch, urgent, BATCH_SIZE);
            moveInto(batch, queued, BATCH_SIZE);
        }
        if (batch.isEmpty()) {
            return;
        }

        String reply;
        try {
            reply = transport.post(settings.serverUrl + "/v1/profiles", requestBody(batch));
        } catch (IOException e) {
            lastStatus = "failed: " + e.getMessage();
            onFailure(batch, now);
            return;
        }

        lastStatus = "ok, " + batch.size() + " players";
        synchronized (this) {
            failures = 0;
        }
        store(reply, now);
    }

    /**
     * Hands everyone waiting to the lists at once. Each list has its own queue, so
     * PvPTiers answering in a blink isn't held up by a slow list, and each answer shows
     * as soon as it's in.
     */
    private void pumpDirect() {
        Map<UUID, String> first = new LinkedHashMap<>();
        Map<UUID, String> rest = new LinkedHashMap<>();
        synchronized (this) {
            moveInto(first, urgent, DIRECT_PER_PUMP);
            moveInto(rest, queued, DIRECT_PER_PUMP - first.size());
        }
        if (first.isEmpty() && rest.isEmpty()) {
            return;
        }
        first.forEach((uuid, name) -> sendDirect(uuid, name, true));
        rest.forEach((uuid, name) -> sendDirect(uuid, name, false));
        lastStatus = "ok, asking the lists about " + (first.size() + rest.size()) + " players";
    }

    private void sendDirect(UUID uuid, String name, boolean urgent) {
        if (!inFlight.add(uuid)) {
            return;
        }
        direct.lookupEach(uuid, name, urgent, new DirectLists.Listener() {
            @Override
            public void answered(UUID player, TierList list, JsonObject result) {
                long now = clock.getAsLong();
                cache.compute(player, (key, old) -> withList(old, player, name, list, result, old == null ? 0 : old.refreshAt(), now));
                dirty = true;
            }

            @Override
            public void finished(UUID player) {
                long now = clock.getAsLong();
                Cached done = cache.computeIfPresent(player, (key, cached) -> new Cached(cached.profile(), cached.raw(), now + freshFor(cached.profile()), now));
                inFlight.remove(player);
                trimCache();
                if (done != null) {
                    retryResting(player, name, done.profile());
                }
            }
        });
    }

    /**
     * A list that was resting answered "not now" for this player. Rather than asking every
     * list again later, ask just that one once it has had time to recover.
     */
    private void retryResting(UUID uuid, String name, PlayerProfile profile) {
        ScheduledExecutorService scheduler = executor;
        if (scheduler == null) {
            return;
        }
        for (TierList list : TierList.values()) {
            if (!"circuit_open".equals(profile.list(list).reason())) {
                continue;
            }
            scheduler.schedule(() -> direct.lookupOne(uuid, name, list, new DirectLists.Listener() {
                @Override
                public void answered(UUID player, TierList answered, JsonObject result) {
                    cache.computeIfPresent(player, (key, old) -> withList(old, player, name, answered, result, old.refreshAt(), clock.getAsLong()));
                    dirty = true;
                }

                @Override
                public void finished(UUID player) {
                }
            }), LIST_RETRY_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    private static void moveInto(Map<UUID, String> batch, Map<UUID, String> source, int size) {
        Iterator<Map.Entry<UUID, String>> entries = source.entrySet().iterator();
        while (entries.hasNext() && batch.size() < size) {
            Map.Entry<UUID, String> entry = entries.next();
            batch.put(entry.getKey(), entry.getValue());
            entries.remove();
        }
    }

    /** Only UUIDs and names leave this PC. The README promises that. */
    private static String requestBody(Map<UUID, String> batch) {
        JsonArray players = new JsonArray();
        batch.forEach((uuid, name) -> {
            JsonObject player = new JsonObject();
            player.addProperty("uuid", uuid.toString());
            player.addProperty("name", name);
            players.add(player);
        });
        JsonObject body = new JsonObject();
        body.add("players", players);
        return body.toString();
    }

    private synchronized void onFailure(Map<UUID, String> batch, long now) {
        failures += 1;
        long delay = FIRST_BACKOFF_MILLIS << Math.min(failures - 1, 10);
        backoffUntil = now + Math.min(delay, MAX_BACKOFF_MILLIS);

        batch.forEach((uuid, name) -> {
            if (queued.size() < MAX_QUEUED) {
                queued.putIfAbsent(uuid, name);
            }
        });
    }

    private void store(String reply, long now) {
        JsonElement root = JsonParser.parseString(reply);
        if (!(root instanceof JsonObject object) || !(object.get("profiles") instanceof JsonArray profiles)) {
            return;
        }

        for (JsonElement element : profiles) {
            if (!(element instanceof JsonObject profileJson)) {
                continue;
            }
            PlayerProfile fresh = ProfileParser.parse(profileJson);
            if (fresh == null) {
                continue;
            }
            JsonObject lists = profileJson.get("lists") instanceof JsonObject found ? found : new JsonObject();
            cache.compute(fresh.uuid(), (key, old) -> {
                Cached merged = old;
                for (TierList list : TierList.values()) {
                    if (lists.get(list.id()) instanceof JsonObject listJson) {
                        merged = withList(merged, fresh.uuid(), fresh.name(), list, listJson, 0, now);
                    }
                }
                return merged == null ? null : new Cached(merged.profile(), merged.raw(), now + freshFor(merged.profile()), now);
            });
        }
        dirty = true;
        trimCache();
    }

    /**
     * One list's answer folded into what we had. A list going down shouldn't wipe what
     * we already knew, so its tags don't flicker off: the last good answer stays until
     * a new good answer arrives.
     */
    private static Cached withList(Cached old, UUID uuid, String name, TierList list, JsonObject listJson, long refreshAt, long checkedAt) {
        ListProfile fresh = ProfileParser.parseList(listJson);
        Map<TierList, ListProfile> lists = new EnumMap<>(TierList.class);
        JsonObject rawLists = new JsonObject();
        if (old != null) {
            lists.putAll(old.profile().lists());
            if (old.raw().get("lists") instanceof JsonObject before) {
                before.entrySet().forEach(entry -> rawLists.add(entry.getKey(), entry.getValue()));
            }
        }
        ListProfile before = lists.get(list);
        if (fresh.status() != ListStatus.UNAVAILABLE || before == null || before.status() == ListStatus.UNAVAILABLE) {
            lists.put(list, fresh);
            rawLists.add(list.id(), listJson);
        }

        String knownName = name != null ? name : old == null ? null : old.profile().name();
        JsonObject raw = new JsonObject();
        raw.addProperty("uuid", uuid.toString());
        raw.addProperty("name", knownName);
        raw.add("lists", rawLists);
        return new Cached(new PlayerProfile(uuid, knownName, lists), raw, refreshAt, old == null ? checkedAt : old.checkedAt());
    }

    /**
     * How long before asking again. `busy` means the server ran out of budget and
     * will have an answer very soon. PVPHQ being down is normal right now, so it
     * doesn't count, or every player would be looked up again and again.
     */
    private static long freshFor(PlayerProfile profile) {
        long wait = FRESH_MILLIS;
        for (TierList list : TierList.values()) {
            ListProfile result = profile.list(list);
            // PVPHQ being down is common, and asking every list again for it isn't worth it.
            if (result.status() != ListStatus.UNAVAILABLE || list == TierList.PVPHQ) {
                continue;
            }
            if ("busy".equals(result.reason())) {
                return RETRY_BUSY_MILLIS;
            }
            wait = Math.min(wait, RETRY_FAILED_MILLIS);
        }
        return wait;
    }

    private void trimCache() {
        int excess = cache.size() - MAX_CACHED;
        if (excess <= 0) {
            return;
        }
        List<Map.Entry<UUID, Cached>> entries = new ArrayList<>(cache.entrySet());
        entries.sort(Map.Entry.comparingByValue((a, b) -> Long.compare(a.checkedAt(), b.checkedAt())));
        for (int i = 0; i < excess; i++) {
            cache.remove(entries.get(i).getKey());
        }
    }

    /**
     * Reads what earlier sessions learned. Each player keeps the time the lists last
     * answered, so anyone checked recently isn't asked again, and the rest show their
     * saved tiers until a fresh answer replaces them.
     */
    void load() {
        if (diskFile == null || !Files.isRegularFile(diskFile)) {
            return;
        }
        long now = clock.getAsLong();
        try {
            JsonElement root = JsonParser.parseString(Files.readString(diskFile, StandardCharsets.UTF_8));
            if (!(root instanceof JsonObject object) || !(object.get("players") instanceof JsonArray players)) {
                return;
            }
            for (JsonElement element : players) {
                if (!(element instanceof JsonObject saved) || !(saved.get("profile") instanceof JsonObject raw)
                        || saved.get("checkedAt") == null || !saved.get("checkedAt").isJsonPrimitive()) {
                    continue;
                }
                long checkedAt = saved.get("checkedAt").getAsLong();
                if (checkedAt > now || now - checkedAt > DISK_MAX_AGE_MILLIS) {
                    continue;
                }
                PlayerProfile profile = ProfileParser.parse(raw);
                if (profile != null && isLookupEligible(profile.uuid())) {
                    cache.put(profile.uuid(), new Cached(profile, raw, checkedAt + freshFor(profile), checkedAt));
                }
            }
            trimCache();
        } catch (IOException | JsonParseException | IllegalStateException | NumberFormatException | UnsupportedOperationException e) {
            // A damaged file only costs us a head start. It's rewritten on the next save.
        }
    }

    private void saveSafely() {
        try {
            save();
        } catch (IOException | RuntimeException e) {
            // Saving is a bonus. Lookups carry on without it.
        }
    }

    /** Writes to a temporary file first, so a crash mid-save can't leave half a file. */
    void save() throws IOException {
        if (diskFile == null) {
            return;
        }
        dirty = false;
        lastSave = clock.getAsLong();
        JsonArray players = new JsonArray();
        for (Cached cached : cache.values()) {
            JsonObject saved = new JsonObject();
            saved.addProperty("checkedAt", cached.checkedAt());
            saved.add("profile", cached.raw());
            players.add(saved);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", DISK_VERSION);
        root.add("players", players);

        Files.createDirectories(diskFile.toAbsolutePath().getParent());
        Path temporary = diskFile.resolveSibling(diskFile.getFileName() + ".tmp");
        Files.writeString(temporary, root.toString(), StandardCharsets.UTF_8);
        Files.move(temporary, diskFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
