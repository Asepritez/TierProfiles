package dev.tierprofiles.core.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.tierprofiles.core.model.PlayerProfile;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Finds players by part of their name. Only McPvP's search matches part of a name, so it
 * supplies most results, and everyone it returns has been tier tested there. Mojang's
 * exact name lookup adds the player with exactly that name, wherever they're ranked, and
 * players this game has already looked up show first, with no call at all.
 */
public final class PlayerSearch {
    /** Minecraft names, and McPvP won't search fewer than 3 letters. */
    private static final Pattern QUERY = Pattern.compile("[A-Za-z0-9_]{3,16}");
    public static final int MAX_RESULTS = 50;
    private static final int MAX_KNOWN = 10;
    /** Mojang knows the name, but it may not be ranked anywhere; their card will say. */
    private static final String EXACT = "Exact name";
    private static final String SEEN = "Seen before";

    /**
     * @param detail a short note such as "#2791 on McPvP" or "Exact name", or empty
     */
    public record Result(UUID uuid, String name, String detail) {
    }

    private final Transport transport;
    private final Executor background;
    private final ProfileService profiles;

    public PlayerSearch(Transport transport, Executor background, ProfileService profiles) {
        this.transport = transport;
        this.background = background;
        this.profiles = profiles;
    }

    public static boolean isSearchable(String query) {
        return query != null && QUERY.matcher(query).matches();
    }

    /** Searches in the background and hands the results to `done`, possibly on another thread. */
    public void search(String query, Consumer<List<Result>> done) {
        if (!isSearchable(query)) {
            done.accept(List.of());
            return;
        }
        background.execute(() -> done.accept(searchNow(query)));
    }

    /** The whole search on this thread. A source that fails is skipped, never fatal. */
    public List<Result> searchNow(String query) {
        if (!isSearchable(query)) {
            return List.of();
        }
        Map<UUID, Result> found = new LinkedHashMap<>();
        Result exact = exactName(query);
        if (exact != null) {
            found.put(exact.uuid(), exact);
        }
        if (profiles != null) {
            for (PlayerProfile known : profiles.knownMatching(query, MAX_KNOWN)) {
                found.putIfAbsent(known.uuid(), new Result(known.uuid(), known.name(), SEEN));
            }
        }
        for (Result result : mcPvp(query)) {
            Result already = found.get(result.uuid());
            // McPvP's rank says more than "Exact name" or "Seen before", so it wins when both know the player.
            if (already == null || already.detail().equals(EXACT) || already.detail().equals(SEEN)) {
                found.put(result.uuid(), new Result(result.uuid(), already == null ? result.name() : already.name(), result.detail()));
            }
            if (found.size() >= MAX_RESULTS) {
                break;
            }
        }
        return new ArrayList<>(found.values());
    }

    private Result exactName(String query) {
        try {
            String body = transport.send("GET", "https://api.mojang.com/users/profiles/minecraft/" + query, null, null);
            if (JsonParser.parseString(body) instanceof JsonObject player) {
                UUID uuid = uuidOf(string(player.get("id")));
                String name = string(player.get("name"));
                return uuid == null || name == null ? null : new Result(uuid, name, EXACT);
            }
        } catch (IOException | JsonParseException | IllegalStateException e) {
            // No one has that exact name, or Mojang is busy. The other sources still count.
        }
        return null;
    }

    private List<Result> mcPvp(String query) {
        List<Result> results = new ArrayList<>();
        try {
            String body = transport.send("GET", "https://www.mcpvp.com/tiers/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8), null, null);
            if (!(JsonParser.parseString(body) instanceof JsonObject data) || !(data.get("players") instanceof JsonArray players)) {
                return results;
            }
            String needle = query.toLowerCase(Locale.ROOT);
            for (JsonElement element : players) {
                if (!(element instanceof JsonObject player)) {
                    continue;
                }
                UUID uuid = uuidOf(string(player.get("uuid")));
                String name = string(player.get("name"));
                if (uuid == null || name == null || !name.toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
                String rank = player.get("rank") != null && player.get("rank").isJsonPrimitive() ? "#" + player.get("rank").getAsString() + " on McPvP" : "";
                results.add(new Result(uuid, name, rank));
            }
        } catch (IOException | JsonParseException | IllegalStateException | UnsupportedOperationException e) {
            // McPvP is down or changed its reply. Exact names and seen players still show.
        }
        return results;
    }

    private static String string(JsonElement element) {
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
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
}
