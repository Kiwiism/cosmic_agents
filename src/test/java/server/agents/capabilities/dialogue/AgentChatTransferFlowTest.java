package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatTransferFlowTest {
    @Test
    void shouldMatchTradeCommandsBeforeChoiceCommands() {
        AgentChatTransferFlow.TransferCommand command = AgentChatTransferFlow.matchTransferCommand("trade me scrolls");

        assertEquals(AgentChatTransferFlow.TransferMode.TRADE, command.mode());
        assertEquals("scrolls", command.category());
    }

    @Test
    void shouldMatchChoiceCommands() {
        AgentChatTransferFlow.TransferCommand command = AgentChatTransferFlow.matchTransferCommand("drop scrolls");

        assertEquals(AgentChatTransferFlow.TransferMode.CHOICE, command.mode());
        assertEquals("scrolls", command.category());
    }

    @Test
    void shouldIgnoreNonTransferCommands() {
        assertNull(AgentChatTransferFlow.matchTransferCommand("follow me"));
    }

    @Test
    void shouldRouteItemQueries() {
        TestItemQueryCallbacks callbacks = new TestItemQueryCallbacks();

        assertTrue(AgentChatTransferFlow.handleItemQuery("do you have orange potion?", callbacks));

        assertEquals("orange potion", callbacks.itemName);
    }

    @Test
    void shouldIgnoreNonItemQueries() {
        TestItemQueryCallbacks callbacks = new TestItemQueryCallbacks();

        assertFalse(AgentChatTransferFlow.handleItemQuery("follow me", callbacks));

        assertNull(callbacks.itemName);
    }

    private static final class TestItemQueryCallbacks implements AgentChatTransferFlow.ItemQueryCallbacks {
        private String itemName;

        @Override
        public void queryItem(String itemName) {
            this.itemName = itemName;
        }
    }
}
