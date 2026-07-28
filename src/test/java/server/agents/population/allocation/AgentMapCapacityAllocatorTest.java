package server.agents.population.allocation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMapCapacityAllocatorTest {
    private final AgentMapCapacityAllocator allocator = new AgentMapCapacityAllocator();

    @Test
    void retainsEligibleCurrentMapBeforeMoving() {
        AgentMapCapacityDecision decision = allocator.select(List.of(
                candidate(100, 0, 4, 5, 8),
                candidate(200, 1, 1, 5, 8)), 200, true, 2).orElseThrow();

        assertEquals(200, decision.candidate().mapId());
        assertEquals(AgentMapCapacityDecision.Reason.RETAIN_ELIGIBLE_CURRENT_MAP,
                decision.reason());
    }

    @Test
    void prefersRankedSoftCapacityThenFallsBackBelowHardCapacity() {
        AgentMapCapacityDecision soft = allocator.select(List.of(
                candidate(100, 0, 5, 5, 8),
                candidate(200, 1, 4, 5, 8)), -1, false, 0).orElseThrow();
        assertEquals(200, soft.candidate().mapId());

        AgentMapCapacityDecision hard = allocator.select(List.of(
                candidate(100, 0, 7, 5, 8),
                candidate(200, 1, 8, 5, 8)), -1, false, 0).orElseThrow();
        assertEquals(100, hard.candidate().mapId());
        assertEquals(AgentMapCapacityDecision.Reason.HIGHEST_RANKED_BELOW_HARD_CAPACITY,
                hard.reason());
    }

    @Test
    void rejectsEveryMapAtHardCapacity() {
        assertTrue(allocator.select(List.of(
                candidate(100, 0, 8, 5, 8)), -1, false, 0).isEmpty());
    }

    private static AgentMapCapacityCandidate candidate(
            int mapId,
            int rank,
            int occupancy,
            int recommended,
            int maximum) {
        return new AgentMapCapacityCandidate(mapId, rank, occupancy, recommended, maximum);
    }
}
