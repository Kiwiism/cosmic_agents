package server.agents.capabilities.supplies;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentPassiveRecoveryPolicyTest {
    @Test
    void shouldReturnBaseHpRecoveryWhenNotStandingStill() {
        assertEquals(10, AgentPassiveRecoveryPolicy.hpRecoveryFromBonuses(10, false, 25));
    }

    @Test
    void shouldAddImprovedHpRecoveryBonusWhenStandingStill() {
        assertEquals(35, AgentPassiveRecoveryPolicy.hpRecoveryFromBonuses(10, true, 25));
    }

    @Test
    void shouldReturnBaseMpRecoveryWhenNotStandingStill() {
        assertEquals(3, AgentPassiveRecoveryPolicy.mpRecoveryFromBonuses(3, false, 10, 20, 30, 40));
    }

    @Test
    void shouldAddAllLegacyMpRecoveryBonusesWhenStandingStill() {
        assertEquals(103, AgentPassiveRecoveryPolicy.mpRecoveryFromBonuses(3, true, 10, 20, 30, 40));
    }
}
