package server.agents.capabilities.supplies;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.contracts.AgentResourceCategory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSupplyRecoveryPolicyTest {
    @Test
    void walletReserveGrowsWithLevelAndRecoveryIsBounded() {
        Character low = character(15, 900, 900, 1_000, 1_000);
        Character high = character(25, 900, 900, 1_000, 1_000);

        assertTrue(AgentSupplyRecoveryPolicy.minimumWalletReserve(high)
                > AgentSupplyRecoveryPolicy.minimumWalletReserve(low));
        assertTrue(AgentSupplyRecoveryPolicy.maximumRecoveryAttempts() > 0);
        assertTrue(AgentSupplyRecoveryPolicy.restTimeoutMs() > 0L);
        assertTrue(AgentSupplyRecoveryPolicy.incomeTimeoutMs() > 0L);
    }

    @Test
    void healthThresholdsGateIncomeRecovery() {
        Character healthy = character(20, 950, 950, 1_000, 1_000);
        Character critical = character(20, 100, 950, 1_000, 1_000);

        assertTrue(AgentSupplyRecoveryPolicy.recoveredForCombat(healthy));
        assertFalse(AgentSupplyRecoveryPolicy.criticallyLowHp(healthy));
        assertTrue(AgentSupplyRecoveryPolicy.criticallyLowHp(critical));
    }

    @Test
    void procurementStateRetainsRequestAcrossRecoveryAndStall() {
        AgentSupplyProcurementState state = new AgentSupplyProcurementState();
        state.start("supply:hp:1", "maintenance:1", AgentResourceCategory.HP_POTION,
                100000000, 1012004, 100000001,
                AgentSupplyProcurementState.Phase.SHOPPING, 0, 200);

        state.beginRecovery(104000000, 200, 1_500, 100L, 1_000L);
        assertEquals(AgentSupplyProcurementState.Phase.RESTING, state.phase());
        assertEquals("supply:hp:1", state.requestId());
        assertEquals(1, state.recoveryAttempts());

        state.markIncomeRecovery(200L, 2_000L);
        state.markStalled("bounded recovery exhausted");
        assertEquals(AgentSupplyProcurementState.Phase.STALLED, state.phase());
        assertTrue(state.isActive());
        assertEquals("bounded recovery exhausted", state.stalledReason());
    }

    private static Character character(
            int level, int hp, int mp, int maximumHp, int maximumMp) {
        Character agent = mock(Character.class);
        when(agent.getLevel()).thenReturn(level);
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getHp()).thenReturn(hp);
        when(agent.getMp()).thenReturn(mp);
        when(agent.getCurrentMaxHp()).thenReturn(maximumHp);
        when(agent.getCurrentMaxMp()).thenReturn(maximumMp);
        return agent;
    }
}
