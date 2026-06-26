package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatSocialFlowTest {
    @Test
    void shouldRouteFameCommands() {
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentChatSocialFlow.handle("fame Alice", callbacks));

        assertEquals("fame:Alice;", callbacks.events);
    }

    @Test
    void shouldIgnoreNonSocialCommands() {
        TestCallbacks callbacks = new TestCallbacks();

        assertFalse(AgentChatSocialFlow.handle("follow me", callbacks));

        assertEquals("", callbacks.events);
    }

    private static final class TestCallbacks implements AgentChatSocialFlow.SocialCallbacks {
        private String events = "";

        @Override
        public void fame(String targetName) {
            events += "fame:" + targetName + ";";
        }
    }
}
