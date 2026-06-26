package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatUtilityFlowTest {
    @Test
    void shouldRouteUtilityCommands() {
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentChatUtilityFlow.handle("trade me", callbacks));
        assertTrue(AgentChatUtilityFlow.handle("sell trash", callbacks));
        assertTrue(AgentChatUtilityFlow.handle("make mob crystals", callbacks));
        assertTrue(AgentChatUtilityFlow.handle("disassemble trash", callbacks));

        assertEquals("trade;sell;make;disassemble;", callbacks.events);
    }

    @Test
    void shouldIgnoreNonUtilityCommands() {
        TestCallbacks callbacks = new TestCallbacks();

        assertFalse(AgentChatUtilityFlow.handle("follow me", callbacks));

        assertEquals("", callbacks.events);
    }

    private static final class TestCallbacks implements AgentChatUtilityFlow.UtilityCallbacks {
        private String events = "";

        @Override
        public void tradeInvite() {
            events += "trade;";
        }

        @Override
        public void sellTrash() {
            events += "sell;";
        }

        @Override
        public void makeCrystals() {
            events += "make;";
        }

        @Override
        public void disassembleTrash() {
            events += "disassemble;";
        }
    }
}
