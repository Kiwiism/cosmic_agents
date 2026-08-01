package net.packet;

import net.opcodes.RecvOpcode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketRateLimiterTest {
    @Test
    void limitsBurstsAndRefillsByConfiguredRate() {
        PacketRateLimiter limiter = new PacketRateLimiter(2, 2, 2, 2, 2, 2, 2, 3);
        short movement = (short) RecvOpcode.MOVE_PLAYER.getValue();

        assertTrue(limiter.allow(movement, 1_000).allowed());
        assertTrue(limiter.allow(movement, 1_000).allowed());
        assertFalse(limiter.allow(movement, 1_000).allowed());
        assertTrue(limiter.allow(movement, 1_500).allowed());
    }

    @Test
    void repeatedViolationsRequestDisconnect() {
        PacketRateLimiter limiter = new PacketRateLimiter(1, 1, 1, 1, 1, 1, 1, 2);
        short chat = (short) RecvOpcode.GENERAL_CHAT.getValue();

        assertTrue(limiter.allow(chat, 1_000).allowed());
        assertFalse(limiter.allow(chat, 1_000).disconnect());
        assertTrue(limiter.allow(chat, 1_000).disconnect());
    }
}
