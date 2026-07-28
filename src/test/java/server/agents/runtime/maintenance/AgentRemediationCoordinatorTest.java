package server.agents.runtime.maintenance;

import org.junit.jupiter.api.Test;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.objectives.AgentObjectiveSource;
import server.agents.objectives.AgentObjectiveStatus;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRemediationCoordinatorTest {
    @Test
    void suspendsAndResumesTheSameForegroundCorrelation() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentObjectiveDefinition foreground = objective("plan:step", "chain-1", 10_000L);
        AgentObjectiveDefinition maintenance = objective("maintenance:supply", "chain-1", 5_000L);
        AgentObjectiveKernel.start(entry, foreground, 100L);
        AgentRemediationFrame frame = new AgentRemediationFrame(
                "supply:1", AgentRemediationKind.LOW_SUPPLIES, maintenance.objectiveId(),
                foreground.correlationId(), 1, 101L, 5_000L, Map.of("hp", "restored"));

        assertTrue(AgentRemediationCoordinator.begin(entry, frame, maintenance,
                "HP potions critical", 101L));
        assertEquals(maintenance, AgentObjectiveKernel.active(entry));
        assertTrue(AgentRemediationCoordinator.finish(entry, frame.frameId(),
                AgentObjectiveStatus.SUCCEEDED, "restored", 200L));
        assertEquals(foreground, AgentObjectiveKernel.active(entry));
        assertFalse(AgentRemediationCoordinator.finish(entry, frame.frameId(),
                AgentObjectiveStatus.SUCCEEDED, "duplicate", 201L));
    }

    @Test
    void rejectsExpiredOrCompetingFrames() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentObjectiveDefinition maintenance = objective("maintenance:supply", "chain-1", 5_000L);
        AgentRemediationFrame expired = new AgentRemediationFrame(
                "expired", AgentRemediationKind.LOW_SUPPLIES, maintenance.objectiveId(),
                "chain-1", 1, 100L, 150L, Map.of());

        assertFalse(AgentRemediationCoordinator.begin(entry, expired, maintenance,
                "expired", 151L));
    }

    private static AgentObjectiveDefinition objective(String id, String correlation, long deadline) {
        return new AgentObjectiveDefinition(id, "test", 100, deadline, 1,
                AgentObjectiveSource.RECOVERY_POLICY, "test-v1", correlation);
    }
}
