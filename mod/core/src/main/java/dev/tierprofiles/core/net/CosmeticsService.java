package dev.tierprofiles.core.net;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.tierprofiles.core.settings.Cosmetics;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Saves your card style and fetches other people's.
 *
 * Saving proves who you are the way Minecraft servers do. We get a challenge from our
 * server, the SessionJoiner tells Mojang you're joining that challenge, and our server
 * asks Mojang to confirm. The access token is only ever used by the joiner, so no method
 * here ever sees it, and it can't reach our server.
 */
public final class CosmeticsService {
    private static final Gson GSON = new Gson();
    private static final long FRESH_MILLIS = 10 * 60 * 1000;
    /** Tokens last 15 minutes on the server. This keeps us clear of the edge. */
    private static final long TOKEN_MILLIS = 12 * 60 * 1000;

    /** Tells Mojang you're joining. Implementations must talk only to Mojang. */
    @FunctionalInterface
    public interface SessionJoiner {
        void join(String serverId) throws IOException;
    }

    public enum SaveState {
        IDLE("Not saved yet"),
        SAVING("Saving your card"),
        SAVED("Saved. Others can see your card"),
        NOT_SIGNED_IN("Sign in to Minecraft to save your card"),
        TOO_SOON("Saved a moment ago. Try again shortly"),
        REFUSED("The server refused this card"),
        OFFLINE("Can't reach the server right now"),
        LOOKUPS_OFF("Turn on online lookups to save your card");

        private final String message;

        SaveState(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    private final Transport transport;
    private final Supplier<String> server;
    private final Executor background;
    private final LongSupplier clock;
    private final CosmeticsCache cache = new CosmeticsCache();
    private final Map<UUID, Long> fetchedAt = new ConcurrentHashMap<>();

    private volatile SessionJoiner joiner;
    private volatile SaveState state = SaveState.IDLE;
    private String token;
    private long tokenExpires;

    public CosmeticsService(Transport transport, Supplier<String> server, SessionJoiner joiner, Executor background, LongSupplier clock) {
        this.transport = transport;
        this.server = server;
        this.joiner = joiner;
        this.background = background;
        this.clock = clock;
    }

    /** For tests: stands in for Mojang, so no real account is ever involved. */
    public void useJoiner(SessionJoiner replacement) {
        joiner = replacement;
    }

    public SaveState state() {
        return state;
    }

    /** What's known about a player's card. Safe while drawing. */
    public Cosmetics get(UUID uuid) {
        return cache.get(uuid);
    }

    public void clear() {
        cache.clear();
        fetchedAt.clear();
    }

    /** Fetches a player's card in the background, unless we have it already. */
    public void request(UUID uuid) {
        long now = clock.getAsLong();
        Long last = fetchedAt.get(uuid);
        if (last != null && now - last < FRESH_MILLIS) {
            return;
        }
        fetchedAt.put(uuid, now);
        background.execute(() -> fetch(uuid));
    }

    private void fetch(UUID uuid) {
        try {
            String reply = transport.send("GET", server.get() + "/v1/cosmetics/" + uuid, null, null);
            JsonElement root = JsonParser.parseString(reply);
            if (root instanceof JsonObject object && object.get("cosmetics") instanceof JsonObject style) {
                cache.put(uuid, GSON.fromJson(style, Cosmetics.class).sanitize());
            }
        } catch (IOException | JsonParseException | IllegalStateException e) {
            // A missing style just means the default card.
        }
    }

    /** Saves in the background. Watch `state` for how it went. */
    public void save(Cosmetics cosmetics, String playerName) {
        state = SaveState.SAVING;
        String body = GSON.toJson(cosmetics);
        background.execute(() -> state = trySave(body, playerName));
    }

    private SaveState trySave(String body, String playerName) {
        try {
            if (token == null || clock.getAsLong() >= tokenExpires) {
                signIn(playerName);
            }
            transport.send("PUT", server.get() + "/v1/cosmetics", body, token);
            return SaveState.SAVED;
        } catch (NotSignedIn e) {
            return SaveState.NOT_SIGNED_IN;
        } catch (Transport.StatusException e) {
            return switch (e.status()) {
                case 401 -> {
                    token = null;
                    yield SaveState.NOT_SIGNED_IN;
                }
                case 403 -> SaveState.NOT_SIGNED_IN;
                case 429 -> SaveState.TOO_SOON;
                case 400 -> SaveState.REFUSED;
                default -> SaveState.OFFLINE;
            };
        } catch (IOException | JsonParseException | IllegalStateException e) {
            return SaveState.OFFLINE;
        }
    }

    private static final class NotSignedIn extends IOException {
    }

    private void signIn(String playerName) throws IOException {
        String base = server.get();
        JsonObject challenge = JsonParser.parseString(transport.post(base + "/v1/auth/challenge", "{}")).getAsJsonObject();

        try {
            joiner.join(challenge.get("serverId").getAsString());
        } catch (IOException e) {
            throw new NotSignedIn();
        }

        JsonObject verify = new JsonObject();
        verify.addProperty("name", playerName);
        verify.addProperty("challenge", challenge.get("challenge").getAsString());
        JsonObject reply = JsonParser.parseString(transport.post(base + "/v1/auth/verify", verify.toString())).getAsJsonObject();

        token = reply.get("token").getAsString();
        tokenExpires = clock.getAsLong() + TOKEN_MILLIS;
    }
}
