package dev.tierprofiles.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.hud.CardState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Hold Tab, use the arrows, press Enter to pin. No Screen ever opens, because
 * opening one releases movement keys, and that would stop you moving mid-fight.
 */
public final class Controls {
    private Controls() {
    }

    /** Returns true when we used the key, so the game shouldn't also act on it. */
    public static boolean onKey(int action, KeyEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (action == InputConstants.RELEASE || minecraft.gui.screen() != null || minecraft.player == null) {
            return false;
        }
        boolean firstPress = action == InputConstants.PRESS;

        if (CardState.isOpen() && firstPress && (event.input() == InputConstants.KEY_ESCAPE || isPinKeyWithListClosed(event))) {
            CardState.close();
            return true;
        }

        if (!TabSelection.isListVisible()) {
            return false;
        }

        if (Keys.UP.matches(event)) {
            TabSelection.move(-1, 0);
            return true;
        }
        if (Keys.DOWN.matches(event)) {
            TabSelection.move(1, 0);
            return true;
        }
        if (Keys.LEFT.matches(event)) {
            TabSelection.move(0, -1);
            return true;
        }
        if (Keys.RIGHT.matches(event)) {
            TabSelection.move(0, 1);
            return true;
        }
        if (Keys.PIN.matches(event) && firstPress) {
            return togglePin();
        }
        return false;
    }

    private static boolean isPinKeyWithListClosed(KeyEvent event) {
        return Keys.PIN.matches(event) && !TabSelection.isListVisible();
    }

    private static boolean togglePin() {
        PlayerInfo info = TabSelection.selected();
        if (info == null) {
            return false;
        }
        if (CardState.isShowing(info.getProfile().id())) {
            CardState.close();
        } else {
            CardState.open(info.getProfile().id(), info.getProfile().name());
            TierProfiles.profiles().requestUrgent(info.getProfile().id(), info.getProfile().name());
        }
        return true;
    }
}
