package dev.tierprofiles.core.net;

import dev.tierprofiles.core.settings.Cosmetics;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Other players' card styles. Filled by the background thread, read while drawing. */
public final class CosmeticsCache {
    private final Map<UUID, Cosmetics> known = new ConcurrentHashMap<>();

    public Cosmetics get(UUID uuid) {
        return known.get(uuid);
    }

    public void put(UUID uuid, Cosmetics cosmetics) {
        known.put(uuid, cosmetics);
    }

    public void clear() {
        known.clear();
    }
}
