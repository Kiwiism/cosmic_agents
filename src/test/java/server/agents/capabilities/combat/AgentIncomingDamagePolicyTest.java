package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentIncomingDamagePolicyTest {
    @Test
    void noMagicGuardLeavesAllDamageOnHp() {
        assertEquals(new AgentIncomingDamagePolicy.DamageSplit(1_000, 0),
                AgentIncomingDamagePolicy.splitMagicGuard(1_000, null, 500));
    }

    @Test
    void magicGuardSplitsDamageByBuffPercentage() {
        assertEquals(new AgentIncomingDamagePolicy.DamageSplit(200, 800),
                AgentIncomingDamagePolicy.splitMagicGuard(1_000, 80, 1_000));
    }

    @Test
    void insufficientMpOverflowsBackToHp() {
        assertEquals(new AgentIncomingDamagePolicy.DamageSplit(990, 10),
                AgentIncomingDamagePolicy.splitMagicGuard(1_000, 80, 10));
    }

    @Test
    void damageAndResourcesAreClampedToSafeBounds() {
        assertEquals(new AgentIncomingDamagePolicy.DamageSplit(0, 0),
                AgentIncomingDamagePolicy.splitMagicGuard(-1, 80, -1));
        assertEquals(new AgentIncomingDamagePolicy.DamageSplit(0, 1_000),
                AgentIncomingDamagePolicy.splitMagicGuard(1_000, 200, 2_000));
    }
}
