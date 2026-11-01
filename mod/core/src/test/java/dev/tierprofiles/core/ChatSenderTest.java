package dev.tierprofiles.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.tierprofiles.core.logic.ChatSender;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatSenderTest {
    private static final List<String> NAMES = List.of("JokingDK", "Marlowww", "VIP");

    @ParameterizedTest
    @ValueSource(strings = {
        "<JokingDK> gg",
        "JokingDK: gg",
        "[Admin] JokingDK: gg",
        "JokingDK » gg",
        "[VIP] JokingDK > hello there",
    })
    void findsTheSenderInCommonFormats(String message) {
        assertEquals("JokingDK", ChatSender.find(message, NAMES));
    }

    @Test
    void ignoresNamesInTheMessageItself() {
        assertNull(ChatSender.find("Stranger: JokingDK is good", NAMES));
    }

    @Test
    void ignoresMessagesThatArentChat() {
        assertNull(ChatSender.find("JokingDK joined the game", NAMES));
    }

    @Test
    void aColonFarIntoTheTextDoesntCount() {
        assertNull(ChatSender.find("x".repeat(70) + " JokingDK: hi", NAMES));
    }
}
