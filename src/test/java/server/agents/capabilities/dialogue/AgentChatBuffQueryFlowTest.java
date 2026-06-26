package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatBuffQueryFlowTest {
    @Test
    void shouldRouteBuffQueryCommands() {
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentChatBuffQueryFlow.handle("buff list", callbacks));
        assertTrue(AgentChatBuffQueryFlow.handle("buff debug", callbacks));
        assertTrue(AgentChatBuffQueryFlow.handle("skill buffs debug", callbacks));

        assertEquals("list;debug;skillDebug;", callbacks.events);
    }

    @Test
    void shouldIgnoreNonBuffQueryCommands() {
        TestCallbacks callbacks = new TestCallbacks();

        assertFalse(AgentChatBuffQueryFlow.handle("follow me", callbacks));

        assertEquals("", callbacks.events);
    }

    private static final class TestCallbacks implements AgentChatBuffQueryFlow.BuffQueryCallbacks {
        private String events = "";

        @Override
        public void reportBuffList() {
            events += "list;";
        }

        @Override
        public void reportBuffDebug() {
            events += "debug;";
        }

        @Override
        public void reportSkillBuffDebug() {
            events += "skillDebug;";
        }
    }
}
