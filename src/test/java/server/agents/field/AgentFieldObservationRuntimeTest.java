package server.agents.field;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentFieldObservationRuntimeTest {
    @Test
    void observationWindowGrowsOneMinutePerAdditionalActiveAgent() {
        assertEquals(120_000L, AgentFieldObservationRuntime.phaseWindowMs(120_000L, 1));
        assertEquals(180_000L, AgentFieldObservationRuntime.phaseWindowMs(120_000L, 2));
        assertEquals(420_000L, AgentFieldObservationRuntime.phaseWindowMs(120_000L, 6));
    }

    @Test
    void completedWindowProducesCapacityCalibrationDeltas() {
        var start = new AgentFieldObservationRuntime.WindowSample(
                2, 1_000L, 1_000L, 1, 8, 0, 0, 0,
                List.of(agent(0, 0, 0, 1, 0, 0, Map.of("SEARCHING", 1_000L))), List.of());
        var end = new AgentFieldObservationRuntime.WindowSample(
                2, 1_000L, 61_000L, 1, 5, 1, 0, 0,
                List.of(agent(10, 500, 12, 2, 1, 0,
                        Map.of("SEARCHING", 11_000L, "IDLE", 5_000L))), List.of());

        var window = AgentFieldObservationRuntime.capacityWindows(List.of(start, end)).getFirst();

        assertEquals(10, window.kills());
        assertEquals(500, window.experience());
        assertEquals(1, window.assignmentChanges());
        assertEquals(1, window.routeFailures());
        assertEquals(2_500, window.nonCombatBasisPoints());
        assertEquals(100_000, window.killsPerAgentMinuteBasisPoints());
        assertEquals(1, window.emptyAssignedPlatforms());
    }

    private static AgentFieldObservationRuntime.AgentSample agent(
            long kills, long experience, long attacks, long assignments,
            long routeFailures, long stuck, Map<String, Long> posture) {
        return new AgentFieldObservationRuntime.AgentSample(
                1, "Agent", 100, 15, true, kills, 0, experience,
                attacks, attacks, 0, 100, assignments, routeFailures, stuck,
                posture, "SEARCHING", "GRINDING");
    }
}
