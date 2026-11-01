package dev.tierprofiles.core.settings;

/** The places a tier can appear. Each is switched and configured on its own. */
public enum Surface {
    NAMETAG("Nametag", true),
    TAB("Tab list", true),
    CHAT("Chat", false),
    LOOK_AT("Look-at card", true),
    OVERLAY("Profile card", true);

    private final String displayName;
    private final boolean enabledByDefault;

    Surface(String displayName, boolean enabledByDefault) {
        this.displayName = displayName;
        this.enabledByDefault = enabledByDefault;
    }

    public String displayName() {
        return displayName;
    }

    public boolean enabledByDefault() {
        return enabledByDefault;
    }
}
