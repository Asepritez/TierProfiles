package dev.tierprofiles.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.tierprofiles.TierProfiles;
import dev.tierprofiles.core.net.ProfileService;
import dev.tierprofiles.core.settings.CardSettings;
import dev.tierprofiles.hud.CardState;
import dev.tierprofiles.hud.LookAtCard;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Hold Tab, use the arrows or the scroll wheel, press Enter to pin. No Screen ever opens, because
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

        if (Keys.ALL_RANKS.matches(event) && firstPress) {
            return showAllRanks();
        }

        if (!TabSelection.isListVisible()) {
            return false;
        }

        if (Keys.UP.matches(event)) {
            return moved(-1, 0);
        }
        if (Keys.DOWN.matches(event)) {
            return moved(1, 0);
        }
        if (Keys.LEFT.matches(event)) {
            return moved(0, -1);
        }
        if (Keys.RIGHT.matches(event)) {
            return moved(0, 1);
        }
        if (Keys.PIN.matches(event) && firstPress) {
            return togglePin();
        }
        return false;
    }

    /**
     * With Tab held, scrolling up or down moves one row, like the arrow keys. Returns true
     * when we used it, so the hotbar doesn't also change.
     */
    public static boolean onScroll(double amount) {
        Minecraft minecraft = Minecraft.getInstance();
        if (amount == 0 || minecraft.gui.screen() != null || minecraft.player == null || !TabSelection.isListVisible()) {
            return false;
        }
        return moved(amount > 0 ? -1 : 1, 0);
    }

    private static boolean isPinKeyWithListClosed(KeyEvent event) {
        return Keys.PIN.matches(event) && !TabSelection.isListVisible();
    }

    /** Moves the selection, and with the big card chosen, shows the newly selected player's. */
    private static boolean moved(int rowsDown, int columnsAcross) {
        TabSelection.move(rowsDown, columnsAcross);
        PlayerInfo info = TabSelection.selected();
        boolean pinnedElsewhere = CardState.isOpen() && !CardState.isPreview();
        if (info != null && !pinnedElsewhere && TierProfiles.settings().card.tabPreview == CardSettings.TabPreview.BIG) {
            CardState.preview(info.getProfile().id(), info.getProfile().name());
            TierProfiles.profiles().requestUrgent(info.getProfile().id(), info.getProfile().name());
            if (ProfileService.isAllowed(TierProfiles.config().global)) {
                TierProfiles.cosmetics().request(info.getProfile().id());
            }
        } else if (info != null) {
            TierProfiles.profiles().requestUrgent(info.getProfile().id(), info.getProfile().name());
        }
        return true;
    }

    private static boolean togglePin() {
        PlayerInfo info = TabSelection.selected();
        if (info == null) {
            return false;
        }
        if (CardState.isShowing(info.getProfile().id()) && CardState.isPreview()) {
            CardState.pin();
        } else if (CardState.isShowing(info.getProfile().id())) {
            CardState.close();
        } else {
            open(info.getProfile().id(), info.getProfile().name(), false);
        }
        return true;
    }

    /**
     * One key for "show me everything": it flips an open card to All ranks and back,
     * or opens one straight into it for the player picked in Tab or under the crosshair.
     */
    private static boolean showAllRanks() {
        PlayerInfo info = TabSelection.isListVisible() ? TabSelection.selected() : null;
        if (info != null && !CardState.isShowing(info.getProfile().id())) {
            open(info.getProfile().id(), info.getProfile().name(), true);
            return true;
        }
        if (CardState.isOpen()) {
            CardState.toggleAllRanks();
            return true;
        }
        UUID aimed = LookAtCard.showing();
        if (aimed != null) {
            open(aimed, LookAtCard.showingName(), true);
            return true;
        }
        return false;
    }

    private static void open(UUID uuid, String name, boolean allRanks) {
        CardState.open(uuid, name, allRanks);
        TierProfiles.profiles().requestUrgent(uuid, name);
        if (ProfileService.isAllowed(TierProfiles.config().global)) {
            TierProfiles.cosmetics().request(uuid);
        }
    }
}
