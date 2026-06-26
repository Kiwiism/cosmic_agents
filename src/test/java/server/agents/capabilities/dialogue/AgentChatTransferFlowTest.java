package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
