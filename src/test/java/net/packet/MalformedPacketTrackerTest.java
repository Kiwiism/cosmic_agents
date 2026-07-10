package net.packet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MalformedPacketTrackerTest {
    @Test
    void shouldTripWithinWindowAndResetAfterIt() {
        MalformedPacketTracker tracker = new MalformedPacketTracker(3, 1_000);

        assertFalse(tracker.record(100));
        assertFalse(tracker.record(200));
        assertTrue(tracker.record(300));
        assertFalse(tracker.record(2_000));
    }
}
