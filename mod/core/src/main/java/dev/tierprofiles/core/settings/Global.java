package dev.tierprofiles.core.settings;

import java.net.URI;
import java.util.Locale;

/**
 * Settings tied to this PC rather than a play style. Kept out of Settings on
 * purpose: a share code can't carry them, so importing a friend's code can't point
 * your client at a different server.
 */
public final class Global {
    public static final String DEFAULT_SERVER_URL = "http://127.0.0.1:8787";

    /** Master switch for sending anything to the TierProfiles server. */
    public boolean lookupsEnabled = true;
    public String serverUrl = DEFAULT_SERVER_URL;
    /** Whether the first-run privacy notice has been shown. */
    public boolean noticeSeen = false;
    /** Turns off every animation in our GUI. */
    public boolean reduceMotion = false;

    public static boolean isValidServerUrl(String url) {
        if (url == null) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    void sanitize() {
        if (!isValidServerUrl(serverUrl)) {
            serverUrl = DEFAULT_SERVER_URL;
        }
        serverUrl = serverUrl.trim().replaceAll("/+$", "");
    }
}
