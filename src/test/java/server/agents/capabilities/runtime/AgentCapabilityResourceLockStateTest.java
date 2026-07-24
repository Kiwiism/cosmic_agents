package server.agents.capabilities.runtime;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCapabilityResourceLockStateTest {
    @Test
    void acquiresAllOrNoneAndSupportsReentrantChildren() {
        AgentCapabilityResourceLockState locks = new AgentCapabilityResourceLockState();
        Set<AgentCapabilityResource> movementAndCombat = Set.of(
                AgentCapabilityResource.MOVEMENT, AgentCapabilityResource.COMBAT);

        assertTrue(locks.acquire("plan:1", movementAndCombat, 10L, 100L));
        assertTrue(locks.acquire("plan:1", Set.of(AgentCapabilityResource.MOVEMENT), 20L, 120L));
        assertFalse(locks.acquire("town:1", Set.of(
                AgentCapabilityResource.MOVEMENT,
                AgentCapabilityResource.INVENTORY), 20L, 120L));
        assertEquals(2, locks.size(20L));

        locks.release("plan:1", Set.of(AgentCapabilityResource.MOVEMENT));
        assertFalse(locks.acquire("town:1",
                Set.of(AgentCapabilityResource.MOVEMENT), 20L, 120L));
        locks.release("plan:1", movementAndCombat);
        assertTrue(locks.acquire("town:1",
                Set.of(AgentCapabilityResource.MOVEMENT), 20L, 120L));
    }

    @Test
    void expiresAbandonedLeases() {
        AgentCapabilityResourceLockState locks = new AgentCapabilityResourceLockState();
        assertTrue(locks.acquire("old", Set.of(AgentCapabilityResource.INVENTORY),
                10L, 20L));

        assertTrue(locks.acquire("new", Set.of(AgentCapabilityResource.INVENTORY),
                20L, 40L));
        assertEquals("new", locks.owners(20L).get(AgentCapabilityResource.INVENTORY));
    }
}
