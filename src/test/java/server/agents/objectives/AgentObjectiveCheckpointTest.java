package server.agents.objectives;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentObjectiveCheckpointTest {
    @Test
    void restoresActiveIntentAndSuspensionStackIntoAFreshState() {
        AgentObjectiveDefinition training = new AgentObjectiveDefinition(
                "training", "progression.victoria-training", 10, 10_000L, 2,
                AgentObjectiveSource.PROGRESSION_POLICY, "v1", "run");
        AgentObjectiveDefinition resupply = new AgentObjectiveDefinition(
                "resupply", "maintenance.resupply", 100, 10_000L, 2,
                AgentObjectiveSource.RECOVERY_POLICY, "v1", "run");
        AgentObjectiveState source = new AgentObjectiveState();
        source.active = resupply;
        source.suspended.addFirst(new AgentObjectiveSuspension(training, "low potions", 100L));

        AgentObjectiveState restored = new AgentObjectiveState();
        restored.restore(source.checkpoint(88, 200L));

        assertEquals(resupply, restored.active());
        assertEquals(training, restored.suspendedSnapshot().getFirst().objective());
    }
}
