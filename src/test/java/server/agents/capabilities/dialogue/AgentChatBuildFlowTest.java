package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatBuildFlowTest {
    @Test
    void shouldRouteSpVariantChoicesWhenAwaitingSelection() {
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentChatBuildFlow.handleSpVariantSelection("1h", true, callbacks));
        assertTrue(AgentChatBuildFlow.handleSpVariantSelection("2h", true, callbacks));

        assertEquals("1h;2h;", callbacks.events);
    }

    @Test
    void shouldIgnoreSpVariantChoicesWhenNotAwaitingSelection() {
        TestCallbacks callbacks = new TestCallbacks();

        assertFalse(AgentChatBuildFlow.handleSpVariantSelection("1h", false, callbacks));

        assertEquals("", callbacks.events);
    }

    @Test
    void shouldIgnoreUnmatchedText() {
        TestCallbacks callbacks = new TestCallbacks();

        assertFalse(AgentChatBuildFlow.handleSpVariantSelection("follow me", true, callbacks));

        assertEquals("", callbacks.events);
    }

    @Test
    void shouldRouteApBuildPromptRequests() {
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentChatBuildFlow.handleApBuildSelection("change build", false, callbacks));

        assertEquals("prompt;", callbacks.events);
    }

    @Test
    void shouldRouteApBuildSelectionOnlyWhenAwaitingSelection() {
        TestCallbacks callbacks = new TestCallbacks();

        assertFalse(AgentChatBuildFlow.handleApBuildSelection("dexless", false, callbacks));
        assertTrue(AgentChatBuildFlow.handleApBuildSelection("dexless", true, callbacks));

        assertEquals("select:dexless;", callbacks.events);
    }

    private static final class TestCallbacks implements AgentChatBuildFlow.SpVariantCallbacks,
            AgentChatBuildFlow.ApBuildCallbacks {
        private String events = "";

        @Override
        public void oneHanded() {
            events += "1h;";
        }

        @Override
        public void twoHanded() {
            events += "2h;";
        }

        @Override
        public void requestBuildPrompt() {
            events += "prompt;";
        }

        @Override
        public void selectBuild(String message) {
            events += "select:" + message + ";";
        }
    }
}
