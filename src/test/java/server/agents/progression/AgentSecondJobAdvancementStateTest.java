package server.agents.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
