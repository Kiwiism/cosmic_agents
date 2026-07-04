package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentPendingActionStateTest {
    @Test
    void storesAndClearsPendingActionAndDropCategory() {
        AgentPendingActionState state = new AgentPendingActionState();

        assertNull(state.pendingAction());
        assertNull(state.pendingDropCategory());

        state.setPendingAction(AgentChatPendingAction.ITEM_CHOICE);
        state.setPendingDropCategory("scrolls");

        assertEquals(AgentChatPendingAction.ITEM_CHOICE, state.pendingAction());
        assertEquals("scrolls", state.pendingDropCategory());

        state.clearPendingAction();
        state.clearPendingDropCategory();

        assertNull(state.pendingAction());
        assertNull(state.pendingDropCategory());
    }
}
