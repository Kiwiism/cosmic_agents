package server.agents.economy.clock;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RealtimeEconomyClockTest {
    @Test
    void advancesLogicalTimeOneForOneAndCapsAtHorizon() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        RealtimeEconomyClock clock = new RealtimeEconomyClock(
                start, start.plusSeconds(120), 1_000_000_000L);

        assertEquals(start, clock.targetAt(1_000_000_000L));
        assertEquals(start.plusMillis(1500), clock.targetAt(2_500_000_000L));
        assertEquals(start.plusSeconds(120), clock.targetAt(500_000_000_000L));
    }

    @Test
    void rejectsBackwardMonotonicTimeAndInvalidHorizon() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        assertThrows(IllegalArgumentException.class,
                () -> new RealtimeEconomyClock(start, start.minusSeconds(1), 5L));
        RealtimeEconomyClock clock = new RealtimeEconomyClock(start, start.plusSeconds(1), 5L);
        assertThrows(IllegalArgumentException.class, () -> clock.targetAt(4L));
    }

    @Test
    void resumeAnchorsAnewAndDoesNotCountStoppedWallTime() {
        Instant persisted = Instant.parse("2026-01-01T00:00:30Z");
        RealtimeEconomyClock resumed = new RealtimeEconomyClock(
                persisted, persisted.plusSeconds(60), 100_000_000_000L);

        assertEquals(persisted, resumed.targetAt(100_000_000_000L));
        assertEquals(persisted.plusSeconds(5), resumed.targetAt(105_000_000_000L));
    }
}
