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

    private static final class TestCallbacks implements AgentChatBuildFlow.SpVariantCallbacks {
        private String events = "";

        @Override
        public void oneHanded() {
            events += "1h;";
        }

        @Override
        public void twoHanded() {
            events += "2h;";
        }
    }
}
