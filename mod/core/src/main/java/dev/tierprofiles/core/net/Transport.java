package dev.tierprofiles.core.net;

import java.io.IOException;

/**
 * Sends one POST and returns the body. It's an interface so tests can pretend to
 * be the server. Calls block, and are only made from the background thread.
 */
public interface Transport {
    /** @throws IOException on a network failure or any status other than 200 */
    String post(String url, String jsonBody) throws IOException;
}
