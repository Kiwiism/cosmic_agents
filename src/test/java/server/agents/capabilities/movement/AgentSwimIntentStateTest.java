package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSwimIntentStateTest {
    @Test
    void defaultsMatchLegacyBotEntryFields() {
        AgentSwimIntentState state = new AgentSwimIntentState();

        assertFalse(state.swimming());
        assertEquals(0, state.moveDirection());
        assertEquals(0, state.verticalHold());
        assertFalse(state.jumpRequested());
        assertEquals(0L, state.nextJumpAtMs());
    }

    @Test
    void normalizesDiscreteSwimInputs() {
        AgentSwimIntentState state = new AgentSwimIntentState();

        state.setMoveDirection(-7);
        state.setVerticalHold(5);

        assertEquals(-1, state.moveDirection());
        assertEquals(1, state.verticalHold());

        state.setMoveDirection(0);
        state.setVerticalHold(0);

        assertEquals(0, state.moveDirection());
        assertEquals(0, state.verticalHold());

        state.setMoveDirection(9);
        state.setVerticalHold(-2);

        assertEquals(1, state.moveDirection());
        assertEquals(-1, state.verticalHold());
    }

    @Test
    void storesModeJumpAndCooldownState() {
        AgentSwimIntentState state = new AgentSwimIntentState();

        state.setSwimming(true);
        state.setJumpRequested(true);
        state.setNextJumpAtMs(1234L);

        assertTrue(state.swimming());
        assertTrue(state.jumpRequested());
        assertEquals(1234L, state.nextJumpAtMs());
    }

    @Test
    void clearsOnlyTransientInput() {
        AgentSwimIntentState state = new AgentSwimIntentState();
        state.setSwimming(true);
        state.setMoveDirection(1);
        state.setVerticalHold(-1);
        state.setJumpRequested(true);
        state.setNextJumpAtMs(77L);

        state.clearInput();

        assertTrue(state.swimming());
        assertEquals(0, state.moveDirection());
        assertEquals(0, state.verticalHold());
        assertFalse(state.jumpRequested());
        assertEquals(77L, state.nextJumpAtMs());
    }
}
