package server.agents.capabilities.behavior;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMapActivityPolicyTest {
    @Test
    void doesNotRestAgentsWhenThereAreEnoughMobs() {
        assertFalse(AgentMapActivityPolicy.shouldAllocateRest(5, 5, 2, 3, 3));
    }

    @Test
    void letsAgentsRespondBeforeTreatingUnclaimedMobsAsScarce() {
        assertFalse(AgentMapActivityPolicy.shouldAllocateRest(5, 2, 0, 5, 2));
    }

    @Test
    void doesNotRestWhileEveryUntargetedAgentStillHasAnUnclaimedMob() {
        assertFalse(AgentMapActivityPolicy.shouldAllocateRest(5, 3, 1, 2, 2));
    }

    @Test
    void restsLowerPriorityAgentsAfterActualMobContentionAppears() {
        assertTrue(AgentMapActivityPolicy.shouldAllocateRest(5, 2, 2, 3, 0));
    }
}
