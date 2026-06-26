package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPendingChatActionFlowTest {
    @Test
    void shouldIgnoreWhenNoPendingActionExists() {
        TestState state = new TestState(null, null);
        TestCallbacks callbacks = new TestCallbacks();

        assertFalse(AgentPendingChatActionFlow.handle(state, "yes", callbacks));
        assertEquals("", callbacks.events);
    }

    @Test
    void shouldDispatchOwnerAwayChoiceWithoutClearingState() {
        TestState state = new TestState(AgentChatPendingAction.OWNER_AWAY, null);
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentPendingChatActionFlow.handle(state, "town", callbacks));

        assertEquals("owner:town;", callbacks.events);
        assertEquals(AgentChatPendingAction.OWNER_AWAY, state.pendingAction());
    }

    @Test
    void shouldDispatchSkillTreeChoiceWithoutClearingState() {
        TestState state = new TestState(AgentChatPendingAction.SKILL_TREE_CHOICE, null);
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentPendingChatActionFlow.handle(state, "fighter", callbacks));

        assertEquals("skill:fighter;", callbacks.events);
        assertEquals(AgentChatPendingAction.SKILL_TREE_CHOICE, state.pendingAction());
    }

    @Test
    void shouldExecuteItemChoiceTradeAndClearState() {
        TestState state = new TestState(AgentChatPendingAction.ITEM_CHOICE, "scrolls");
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentPendingChatActionFlow.handle(state, "trade me", callbacks));

        assertEquals("item:scrolls:true;", callbacks.events);
        assertNull(state.pendingAction());
        assertNull(state.pendingDropCategory());
    }

    @Test
    void shouldExecuteItemChoiceDropAndClearState() {
        TestState state = new TestState(AgentChatPendingAction.ITEM_CHOICE, "scrolls");
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentPendingChatActionFlow.handle(state, "drop them", callbacks));

        assertEquals("item:scrolls:false;", callbacks.events);
        assertNull(state.pendingAction());
        assertNull(state.pendingDropCategory());
    }

    @Test
    void shouldCancelItemChoiceOnOtherResponseAndClearState() {
        TestState state = new TestState(AgentChatPendingAction.ITEM_CHOICE, "scrolls");
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentPendingChatActionFlow.handle(state, "never mind", callbacks));

        assertEquals("itemCancel;", callbacks.events);
        assertNull(state.pendingAction());
        assertNull(state.pendingDropCategory());
    }

    @Test
    void shouldConfirmRelogAndClearPendingAction() {
        TestState state = new TestState(AgentChatPendingAction.RELOG, null);
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentPendingChatActionFlow.handle(state, "yes", callbacks));

        assertEquals("relog;", callbacks.events);
        assertNull(state.pendingAction());
    }

    @Test
    void shouldConfirmLogoutAndClearPendingAction() {
        TestState state = new TestState(AgentChatPendingAction.LOGOUT, null);
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentPendingChatActionFlow.handle(state, "confirm", callbacks));

        assertEquals("logout;", callbacks.events);
        assertNull(state.pendingAction());
    }

    @Test
    void shouldCancelUnknownPendingActionAndPreserveDropCancelFlag() {
        TestState state = new TestState("drop_scrolls", null);
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentPendingChatActionFlow.handle(state, "no", callbacks));

        assertEquals("cancel:true;", callbacks.events);
        assertNull(state.pendingAction());
    }

    private static final class TestState implements AgentPendingChatActionFlow.PendingActionState {
        private String pendingAction;
        private String pendingDropCategory;

        private TestState(String pendingAction, String pendingDropCategory) {
            this.pendingAction = pendingAction;
            this.pendingDropCategory = pendingDropCategory;
        }

        @Override
        public String pendingAction() {
            return pendingAction;
        }

        @Override
        public String pendingDropCategory() {
            return pendingDropCategory;
        }

        @Override
        public void clearPendingAction() {
            pendingAction = null;
        }

        @Override
        public void clearPendingDropCategory() {
            pendingDropCategory = null;
        }
    }

    private static final class TestCallbacks implements AgentPendingChatActionFlow.PendingActionCallbacks {
        private String events = "";

        @Override
        public void handleOwnerAwayChoice(String message) {
            events += "owner:" + message + ";";
        }

        @Override
        public void executeItemChoice(String category, boolean trade) {
            events += "item:" + category + ":" + trade + ";";
        }

        @Override
        public void cancelItemChoice() {
            events += "itemCancel;";
        }

        @Override
        public void handleSkillTreeChoice(String message) {
            events += "skill:" + message + ";";
        }

        @Override
        public void confirmRelog() {
            events += "relog;";
        }

        @Override
        public void confirmLogout() {
            events += "logout;";
        }

        @Override
        public void cancelPendingAction(boolean dropAction) {
            events += "cancel:" + dropAction + ";";
        }
    }
}
