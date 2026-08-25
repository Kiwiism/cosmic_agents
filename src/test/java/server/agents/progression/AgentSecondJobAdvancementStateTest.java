package server.agents.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSecondJobAdvancementStateTest {
    @Test
    void branchCommitmentIsStableAndPhaseChangesAreJournaled() {
        AgentSecondJobAdvancementState state = new AgentSecondJobAdvancementState();
        state.begin("fighter", 10L);
        state.phase(AgentSecondJobAdvancementState.Phase.LEADER, "go to Balrog", 20L);
        state.phase(AgentSecondJobAdvancementState.Phase.TRIAL, "collect marbles", 30L);

        assertEquals("fighter", state.branchId());
        assertEquals(3, state.journalSnapshot().size());
        assertEquals(AgentSecondJobAdvancementState.Phase.TRIAL,
                state.journalSnapshot().getLast().phase());
        assertThrows(IllegalStateException.class, () -> state.begin("page", 40L));
    }

    @Test
    void unchangedTrialItemsRequestBoundedPlatformRebalancing() {
        AgentSecondJobAdvancementState state = new AgentSecondJobAdvancementState();
        state.begin("gunslinger", 1L);

        assertFalse(state.trialRebalanceDue(7, 10L, 15_000L));
        assertFalse(state.trialRebalanceDue(7, 15_009L, 15_000L));
        assertTrue(state.trialRebalanceDue(7, 15_010L, 15_000L));
        assertFalse(state.trialRebalanceDue(8, 15_011L, 15_000L));
    }
}
