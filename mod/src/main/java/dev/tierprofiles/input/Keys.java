package dev.tierprofiles.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.tierprofiles.TierProfiles;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** Our keys. Registering them puts them in the game's Controls screen, so all are rebindable. */
public final class Keys {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(TierProfiles.MOD_ID, "main"));

    public static final KeyMapping UP = key("up", InputConstants.KEY_UP);
    public static final KeyMapping DOWN = key("down", InputConstants.KEY_DOWN);
    public static final KeyMapping LEFT = key("left", InputConstants.KEY_LEFT);
    public static final KeyMapping RIGHT = key("right", InputConstants.KEY_RIGHT);
    public static final KeyMapping PIN = key("pin", InputConstants.KEY_RETURN);
    public static final KeyMapping SETTINGS = key("settings", InputConstants.KEY_F12);

    private Keys() {
    }

    private static KeyMapping key(String name, int defaultKey) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.tierprofiles." + name, defaultKey, CATEGORY));
    }

    /** Touching this class registers the keys, which must happen during startup. */
    public static void register() {
    }
}
