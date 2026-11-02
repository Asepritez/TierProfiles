package dev.tierprofiles.core.net;

import java.io.IOException;

/**
 * One HTTP call that returns the body. It's an interface so tests can pretend to be the
 * server. Calls block, and are only made from background threads.
 */
public interface Transport {
    /** A status other than 200. */
    final class StatusException extends IOException {
        private final int status;

        public StatusException(int status) {
            super("server answered " + status);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    /**
     * @param body   the JSON to send, or null
     * @param bearer a sign-in token to send, or null
     * @throws IOException on a network failure or any status other than 200
     */
    String send(String method, String url, String body, String bearer) throws IOException;

    default String post(String url, String jsonBody) throws IOException {
        return send("POST", url, jsonBody, null);
    }
}
