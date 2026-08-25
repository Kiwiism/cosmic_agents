package server.agents.economy.clock;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimulationKernelTest {
    @Test
    void advancesBetweenEventsInStableOrderAndNeverRewinds() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        LogicalClock clock = new LogicalClock(start);
        LogicalEventQueue queue = new LogicalEventQueue();
        queue.schedule(start.plusSeconds(20), "later", "2", Map.of());
        queue.schedule(start.plusSeconds(10), "first", "1", Map.of());
        queue.schedule(start.plusSeconds(10), "second", "2", Map.of());
        var handled = new ArrayList<String>();

        var result = new SimulationKernel(clock, queue, 10)
                .advanceUntil(start.plusSeconds(30), event -> handled.add(event.kind()));

        assertEquals(java.util.List.of("first", "second", "later"), handled);
        assertEquals(start.plusSeconds(30), result.reachedAt());
        assertThrows(IllegalArgumentException.class,
                () -> clock.advanceTo(start.minusSeconds(1)));
    }

    @Test
    void stopsAtBatchLimitWithoutSkippingDueEvents() {
        Instant start = Instant.EPOCH;
        LogicalClock clock = new LogicalClock(start);
        LogicalEventQueue queue = new LogicalEventQueue();
        for (int i = 0; i < 3; i++) queue.schedule(start.plusSeconds(i), "event", "", Map.of());

        var result = new SimulationKernel(clock, queue, 2)
                .advanceUntil(start.plusSeconds(10), ignored -> { });

        assertTrue(result.batchLimitReached());
        assertEquals(start.plusSeconds(1), result.reachedAt());
        assertEquals(1, result.queuedEvents());
    }

    @Test
    void exclusiveAdvanceLeavesNewDayEventsQueuedAtTheBoundary() {
        Instant start = Instant.EPOCH;
        Instant boundary = start.plusSeconds(86_400);
        LogicalClock clock = new LogicalClock(start);
        LogicalEventQueue queue = new LogicalEventQueue();
        queue.schedule(boundary.minusSeconds(1), "old-day", "", Map.of());
        queue.schedule(boundary, "new-day", "", Map.of());
        var handled = new ArrayList<String>();

        var close = new SimulationKernel(clock, queue, 10)
                .advanceUntilExclusive(boundary, event -> handled.add(event.kind()), () -> false);

        assertEquals(java.util.List.of("old-day"), handled);
        assertEquals(boundary, close.reachedAt());
        assertEquals(1, close.queuedEvents());
        new SimulationKernel(clock, queue, 10).advanceUntil(boundary, event -> handled.add(event.kind()));
        assertEquals(java.util.List.of("old-day", "new-day"), handled);
    }
}
