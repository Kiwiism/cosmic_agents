package server.agents.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentMushroomKingdomRecoveryPolicyTest {
    @Test
    void dialogueRecoveryEscalatesWithoutSkippingStages() {
        assertEquals(AgentMushroomKingdomRecoveryPolicy.Action.NONE,
                AgentMushroomKingdomRecoveryPolicy.next(29_999L, false, 0, 0));
        assertEquals(AgentMushroomKingdomRecoveryPolicy.Action.RESET_TRANSIENT,
                AgentMushroomKingdomRecoveryPolicy.next(30_000L, false, 0, 0));
        assertEquals(AgentMushroomKingdomRecoveryPolicy.Action.STAGE_LOCAL,
                AgentMushroomKingdomRecoveryPolicy.next(90_000L, false, 1, 0));
        assertEquals(AgentMushroomKingdomRecoveryPolicy.Action.RESET_CHECKPOINT,
                AgentMushroomKingdomRecoveryPolicy.next(180_000L, false, 2, 0));
        assertEquals(AgentMushroomKingdomRecoveryPolicy.Action.RECONCILE,
                AgentMushroomKingdomRecoveryPolicy.next(300_000L, false, 3, 1));
        assertEquals(AgentMushroomKingdomRecoveryPolicy.Action.BLOCK,
                AgentMushroomKingdomRecoveryPolicy.next(600_000L, false, 4, 1));
    }

    @Test
    void huntingGetsLongerRecoveryWindows() {
        assertEquals(AgentMushroomKingdomRecoveryPolicy.Action.NONE,
                AgentMushroomKingdomRecoveryPolicy.next(179_999L, true, 0, 0));
        assertEquals(AgentMushroomKingdomRecoveryPolicy.Action.RESET_TRANSIENT,
                AgentMushroomKingdomRecoveryPolicy.next(180_000L, true, 0, 0));
        assertEquals(AgentMushroomKingdomRecoveryPolicy.Action.RECONCILE,
                AgentMushroomKingdomRecoveryPolicy.next(30 * 60_000L, true, 3, 1));
        assertEquals(AgentMushroomKingdomRecoveryPolicy.Action.BLOCK,
                AgentMushroomKingdomRecoveryPolicy.next(45 * 60_000L, true, 4, 1));
    }
}
