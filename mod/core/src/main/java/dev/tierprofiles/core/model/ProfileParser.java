package dev.tierprofiles.core.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Turns the server's JSON into a PlayerProfile, skipping anything malformed. */
public final class ProfileParser {
    private ProfileParser() {
    }

    /** Returns null if there's no usable UUID. */
    public static PlayerProfile parse(JsonObject json) {
        UUID uuid = uuidOf(string(json, "uuid"));
        if (uuid == null) {
            return null;
        }

        Map<TierList, ListProfile> lists = new EnumMap<>(TierList.class);
        JsonObject listsJson = object(json, "lists");
        if (listsJson != null) {
            for (TierList list : TierList.values()) {
                JsonObject listJson = object(listsJson, list.id());
                if (listJson != null) {
                    lists.put(list, parseList(listJson));
                }
            }
        }
        return new PlayerProfile(uuid, string(json, "name"), lists);
    }

    /** One list's answer, in the shape the server sends inside `lists`. */
    public static ListProfile parseList(JsonObject json) {
        String status = string(json, "status");
        if (status == null) {
            return ListProfile.unavailable("bad_response");
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "ok" -> parseOk(json);
            case "unranked" -> ListProfile.UNRANKED;
            default -> ListProfile.unavailable(string(json, "reason"));
        };
    }

    private static ListProfile parseOk(JsonObject json) {
        List<TierEntry> tiers = new ArrayList<>();
        if (json.get("tiers") instanceof JsonArray array) {
            for (JsonElement element : array) {
                if (element instanceof JsonObject tier) {
                    TierEntry entry = parseEntry(tier);
                    if (entry != null) {
                        tiers.add(entry);
                    }
                }
            }
        }
        return new ListProfile(
                ListStatus.OK,
                null,
                string(json, "region"),
                string(json, "country"),
                number(json, "points"),
                integer(json, "overall"),
                List.copyOf(tiers));
    }

    private static TierEntry parseEntry(JsonObject json) {
        String kit = string(json, "kit");
        Tier rank = Tier.parse(string(json, "rank"));
        if (kit == null || rank == null) {
            return null;
        }
        Double attained = number(json, "attained");
        JsonElement retired = json.get("retired");
        return new TierEntry(
                kit,
                rank,
                Tier.parse(string(json, "peak")),
                attained != null ? attained.longValue() : null,
                retired != null && retired.isJsonPrimitive() && retired.getAsBoolean(),
                integer(json, "rating"),
                integer(json, "games"));
    }

    private static UUID uuidOf(String text) {
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static JsonObject object(JsonObject json, String key) {
        return json.get(key) instanceof JsonObject value ? value : null;
    }

    private static String string(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static Double number(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                ? value.getAsDouble()
                : null;
    }

    private static Integer integer(JsonObject json, String key) {
        Double value = number(json, key);
        return value != null ? value.intValue() : null;
    }
}
