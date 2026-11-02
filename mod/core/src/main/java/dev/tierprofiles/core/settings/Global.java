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

    /** Master switch for looking anyone up at all. */
    public boolean lookupsEnabled = true;
    /** Where lookups go. Direct needs no server of ours, so it's the default until one is hosted. */
    public Source source = Source.DIRECT;
    public String serverUrl = DEFAULT_SERVER_URL;

    public enum Source {
        /** Ask each list's own website. Works with nothing else running. */
        DIRECT,
        /** Ask the TierProfiles server, which asks the lists for you. */
        SERVER
    }

    /**
     * Load other players' card styles from the TierProfiles server, and save yours there.
     * Separate from Source: tiers can come straight from the lists while styles still come
     * from us, because only our server knows what card someone picked.
     */
    public boolean cardStyles = true;

    /** The server to ask for card styles, or null when that's off or not yet agreed to. */
    public String cardStyleServer() {
        return lookupsEnabled && noticeSeen && cardStyles ? serverUrl : null;
    }

    public boolean usesServer() {
        return source == Source.SERVER;
    }
    /** Whether the first-run privacy notice has been shown. */
    public boolean noticeSeen = false;
    /** Turns off every animation in our GUI. */
    public boolean reduceMotion = false;
    /** Download each list's real logo from its own website. */
    public boolean officialLogos = true;
    /** How your own card looks. Personal, so share codes never carry it. */
    public Cosmetics cosmetics = new Cosmetics();

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
        source = source == null ? Source.DIRECT : source;
        if (!isValidServerUrl(serverUrl)) {
            serverUrl = DEFAULT_SERVER_URL;
        }
        serverUrl = serverUrl.trim().replaceAll("/+$", "");
        cosmetics = cosmetics == null ? new Cosmetics() : cosmetics;
        cosmetics.sanitize();
    }
}
