package dev.tierprofiles.core.logic;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Many PvP servers send chat as plain system messages, with no sender attached. This
 * guesses the sender from the start of the message. It's only used to choose which
 * tag to show, and a wrong guess only shows that other player's real tier.
 */
public final class ChatSender {
    private static final int LONGEST_PREFIX = 64;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern END_OF_PREFIX = Pattern.compile("[:»>]");

    private ChatSender() {
    }

    /** The last known name before the first colon or arrow, or null. */
    public static String find(String message, Collection<String> names) {
        Matcher end = END_OF_PREFIX.matcher(message);
        if (!end.find() || end.start() > LONGEST_PREFIX) {
            return null;
        }

        Matcher name = NAME.matcher(message.substring(0, end.start()));
        String found = null;
        while (name.find()) {
            if (names.contains(name.group())) {
                found = name.group();
            }
        }
        return found;
    }
}
