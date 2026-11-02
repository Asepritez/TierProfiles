package dev.tierprofiles.core.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tierprofiles.core.model.ListProfile;
import dev.tierprofiles.core.model.ListStatus;
import dev.tierprofiles.core.model.PlayerProfile;
import dev.tierprofiles.core.model.ProfileParser;
import dev.tierprofiles.core.model.TierList;
import dev.tierprofiles.core.settings.Global;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Finds out players' tiers without ever making the game wait.
 *
 * The game thread only calls `request` and `profile`, which touch memory and return
 * at once. A single background thread drains the queue in batches and fills the
 * cache. If anything goes wrong, the only effect is that a tag appears later.
 */
public final class ProfileService {
    public static final int BATCH_SIZE = 8;

    static final long FRESH_MILLIS = 10 * 60 * 1000;
    static final long RETRY_BUSY_MILLIS = 30 * 1000;
    static final long RETRY_FAILED_MILLIS = 2 * 60 * 1000;
    static final long FIRST_BACKOFF_MILLIS = 5 * 1000;
    static final long MAX_BACKOFF_MILLIS = 5 * 60 * 1000;
    static final int MAX_CACHED = 4096;
    static final int MAX_QUEUED = 1024;

    private static final long PUMP_INTERVAL_MILLIS = 250;

    private record Cached(PlayerProfile profile, long refreshAt) {
    }

    private final Transport transport;
    private final Supplier<Global> global;
    private final LongSupplier clock;

    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    // Guarded by `this`. The maps keep names to send, in the order they were asked for.
    private final Map<UUID, String> urgent = new LinkedHashMap<>();
    private final Map<UUID, String> queued = new LinkedHashMap<>();

    private volatile String lastStatus = "nothing sent yet";
    private long backoffUntil;
    private int failures;
    private ScheduledExecutorService executor;

    public ProfileService(Transport transport, Supplier<Global> global, LongSupplier clock) {
        this.transport = transport;
        this.global = global;
        this.clock = clock;
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

    private void enqueue(UUID uuid, String name, boolean first) {
        if (!isLookupEligible(uuid) || !isAllowed(global.get())) {
            return;
        }
        Cached cached = cache.get(uuid);
        if (cached != null && cached.refreshAt() > clock.getAsLong()) {
            return;
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
        return urgent.size() + queued.size();
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

    /** Forget everything, for example after switching to another server. */
    public void clear() {
        cache.clear();
        synchronized (this) {
            urgent.clear();
            queued.clear();
            backoffUntil = 0;
            failures = 0;
        }
    }

    public void start() {
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
    }

    private void pumpSafely() {
        try {
            pump();
        } catch (RuntimeException e) {
            // One bad reply must not end the repeating task, or lookups stop for good.
        }
    }

    /** Sends one batch, if there's one to send. Package-private so tests can step it. */
    void pump() {
        Global settings = global.get();
        if (!isAllowed(settings)) {
            synchronized (this) {
                urgent.clear();
                queued.clear();
            }
            return;
        }

        long now = clock.getAsLong();
        Map<UUID, String> batch;
        synchronized (this) {
            if (now < backoffUntil) {
                return;
            }
            batch = takeBatch();
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

    private Map<UUID, String> takeBatch() {
        Map<UUID, String> batch = new LinkedHashMap<>();
        moveInto(batch, urgent);
        moveInto(batch, queued);
        return batch;
    }

    private static void moveInto(Map<UUID, String> batch, Map<UUID, String> source) {
        Iterator<Map.Entry<UUID, String>> entries = source.entrySet().iterator();
        while (entries.hasNext() && batch.size() < BATCH_SIZE) {
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
            if (element instanceof JsonObject profileJson) {
                PlayerProfile fresh = ProfileParser.parse(profileJson);
                if (fresh != null) {
                    keep(mergeWithOld(fresh), now);
                }
            }
        }
        trimCache();
    }

    /**
     * A list going down shouldn't wipe what we already knew, so its tags don't
     * flicker off. The last good answer stays until a new good answer arrives.
     */
    private PlayerProfile mergeWithOld(PlayerProfile fresh) {
        Cached old = cache.get(fresh.uuid());
        if (old == null) {
            return fresh;
        }
        Map<TierList, ListProfile> lists = new EnumMap<>(TierList.class);
        lists.putAll(fresh.lists());
        for (TierList list : TierList.values()) {
            ListProfile before = old.profile().list(list);
            if (fresh.list(list).status() == ListStatus.UNAVAILABLE && before.status() != ListStatus.UNAVAILABLE) {
                lists.put(list, before);
            }
        }
        return new PlayerProfile(fresh.uuid(), fresh.name(), lists);
    }

    private void keep(PlayerProfile profile, long now) {
        cache.put(profile.uuid(), new Cached(profile, now + freshFor(profile)));
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
            if (result.status() != ListStatus.UNAVAILABLE) {
                continue;
            }
            if ("busy".equals(result.reason())) {
                return RETRY_BUSY_MILLIS;
            }
            if (list != TierList.PVPHQ) {
                wait = Math.min(wait, RETRY_FAILED_MILLIS);
            }
        }
        return wait;
    }

    private void trimCache() {
        int excess = cache.size() - MAX_CACHED;
        if (excess <= 0) {
            return;
        }
        List<Map.Entry<UUID, Cached>> entries = new ArrayList<>(cache.entrySet());
        entries.sort(Map.Entry.comparingByValue((a, b) -> Long.compare(a.refreshAt(), b.refreshAt())));
        for (int i = 0; i < excess; i++) {
            cache.remove(entries.get(i).getKey());
        }
    }
}
