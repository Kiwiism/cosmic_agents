package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRangedEngagementPolicyTest {
    @Test
    void permitsOneCloseAttackBeforeRetreating() {
        AgentRangedEngagementPolicy.Decision decision = AgentRangedEngagementPolicy.decide(
                new AgentRangedEngagementPolicy.Input(true, false, false, true, false, false));

        assertTrue(decision.allowOneDegenerateAttack());
        assertFalse(decision.retreat());
        assertTrue(decision.attackGateOpen());
    }

    @Test
    void retreatsAfterCloseAttackWasCommitted() {
        AgentRangedEngagementPolicy.Decision decision = AgentRangedEngagementPolicy.decide(
                new AgentRangedEngagementPolicy.Input(true, true, false, true, false, false));

        assertFalse(decision.allowOneDegenerateAttack());
        assertTrue(decision.retreat());
        assertFalse(decision.attackGateOpen());
    }

    @Test
    void keepsAttackGateOpenWhenProjectileCanStillFire() {
        AgentRangedEngagementPolicy.Decision decision = AgentRangedEngagementPolicy.decide(
                new AgentRangedEngagementPolicy.Input(false, false, true, true, true, false));

        assertFalse(decision.allowOneDegenerateAttack());
        assertTrue(decision.retreat());
        assertTrue(decision.attackGateOpen());
    }

    @Test
    void bossSpacingBlocksTheCloseFallbackUntilTheAgentRetreats() {
        AgentRangedEngagementPolicy.Decision decision = AgentRangedEngagementPolicy.decide(
                new AgentRangedEngagementPolicy.Input(true, false, false, true, false, true));

        assertFalse(decision.allowOneDegenerateAttack());
        assertTrue(decision.retreat());
        assertFalse(decision.attackGateOpen());
    }
}
