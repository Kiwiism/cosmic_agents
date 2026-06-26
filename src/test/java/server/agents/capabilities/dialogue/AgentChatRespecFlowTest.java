package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatRespecFlowTest {
    @Test
    void shouldRouteRespecCommands() {
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentChatRespecFlow.handle("reset ap", callbacks));
        assertTrue(AgentChatRespecFlow.handle("respec", callbacks));

        assertEquals("ap;sp;", callbacks.events);
    }

    @Test
    void shouldIgnoreNonRespecCommands() {
        TestCallbacks callbacks = new TestCallbacks();

        assertFalse(AgentChatRespecFlow.handle("follow me", callbacks));

        assertEquals("", callbacks.events);
    }

    private static final class TestCallbacks implements AgentChatRespecFlow.RespecCallbacks {
        private String events = "";

        @Override
        public void respecAp() {
            events += "ap;";
        }

        @Override
        public void respecSp() {
            events += "sp;";
        }
    }
}
