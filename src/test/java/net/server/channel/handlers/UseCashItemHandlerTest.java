package net.server.channel.handlers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UseCashItemHandlerTest {
    @Test
    void validatesChalkboardMessageLength() {
        assertFalse(UseCashItemHandler.isValidChalkboardMessage(null));
        assertFalse(UseCashItemHandler.isValidChalkboardMessage(" "));
        assertTrue(UseCashItemHandler.isValidChalkboardMessage(
                "x".repeat(UseCashItemHandler.MAX_CHALKBOARD_MESSAGE_LENGTH)));
        assertFalse(UseCashItemHandler.isValidChalkboardMessage(
                "x".repeat(UseCashItemHandler.MAX_CHALKBOARD_MESSAGE_LENGTH + 1)));
    }
}
