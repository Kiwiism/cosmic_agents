package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentMovementSubstepPolicyTest {
    @Test
    void convertsBackgroundCadenceIntoPhysicsSizedSteps() {
        assertEquals(5, AgentMovementSubstepPolicy.substeps(250L, 50));
        assertEquals(2, AgentMovementSubstepPolicy.substeps(100L, 50));
    }

    @Test
    void boundsCatchUpAndInvalidCadences() {
        assertEquals(5, AgentMovementSubstepPolicy.substeps(1_000L, 50));
        assertEquals(1, AgentMovementSubstepPolicy.substeps(0L, 50));
        assertEquals(1, AgentMovementSubstepPolicy.substeps(250L, 0));
    }
}
