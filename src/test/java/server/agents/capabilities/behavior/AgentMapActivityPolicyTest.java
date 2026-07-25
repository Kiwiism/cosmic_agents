package server.agents.capabilities.behavior;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void scalesProfileAvoidanceByActualMobScarcity() {
        assertEquals(0, AgentMapActivityPolicy.mobScarcityPercent(10, 10, 100));
        assertEquals(20, AgentMapActivityPolicy.mobScarcityPercent(10, 8, 100));
        assertEquals(50, AgentMapActivityPolicy.mobScarcityPercent(10, 5, 100));
        assertEquals(80, AgentMapActivityPolicy.mobScarcityPercent(10, 2, 100));
        assertEquals(100, AgentMapActivityPolicy.mobScarcityPercent(10, 0, 100));

        assertEquals(0, AgentMapActivityPolicy.effectiveAvoidancePercent(85, 0));
        assertEquals(17, AgentMapActivityPolicy.effectiveAvoidancePercent(85, 20));
        assertEquals(42, AgentMapActivityPolicy.effectiveAvoidancePercent(85, 50));
        assertEquals(68, AgentMapActivityPolicy.effectiveAvoidancePercent(85, 80));
        assertEquals(85, AgentMapActivityPolicy.effectiveAvoidancePercent(85, 100));
    }
}
