package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatSessionRequestFlowTest {
    @Test
    void shouldDispatchRelogRequests() {
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentChatSessionRequestFlow.handle("save and relog", callbacks));

        assertEquals("relog;", callbacks.events);
    }

    @Test
    void shouldDispatchLogoutRequests() {
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentChatSessionRequestFlow.handle("save and logout", callbacks));

        assertEquals("logout;", callbacks.events);
    }

    @Test
    void shouldDispatchAwayRequests() {
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentChatSessionRequestFlow.handle("brb", callbacks));

        assertEquals("away;", callbacks.events);
    }

    @Test
    void shouldIgnoreNonSessionRequests() {
        TestCallbacks callbacks = new TestCallbacks();

        assertFalse(AgentChatSessionRequestFlow.handle("follow me", callbacks));

        assertEquals("", callbacks.events);
    }

    @Test
    void shouldSelectRepliesFromSessionDialoguePools() {
        assertTrue(AgentDialogueCatalog.relogConfirmPrompts().contains(AgentChatSessionRequestFlow.relogConfirmPrompt()));
        assertTrue(AgentDialogueCatalog.logoutConfirmPrompts().contains(AgentChatSessionRequestFlow.logoutConfirmPrompt()));
        assertTrue(AgentDialogueCatalog.relogConfirmedReplies().contains(AgentChatSessionRequestFlow.relogConfirmedReply()));
        assertTrue(AgentDialogueCatalog.logoutConfirmedReplies().contains(AgentChatSessionRequestFlow.logoutConfirmedReply()));
    }

    private static final class TestCallbacks implements AgentChatSessionRequestFlow.SessionRequestCallbacks {
        private String events = "";

        @Override
        public void requestRelog() {
            events += "relog;";
        }

        @Override
        public void requestLogout() {
            events += "logout;";
        }

        @Override
        public void requestAway() {
            events += "away;";
        }
    }
}
