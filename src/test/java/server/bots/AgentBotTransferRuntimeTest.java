package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatTransferFlow;
import server.agents.capabilities.trade.AgentInventoryTransferService;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotTransferRuntime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotTransferRuntimeTest {
    @Test
    void weirdTransferRequestRepliesImmediately() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentChatTransferFlow.TransferCommand command =
                new AgentChatTransferFlow.TransferCommand(AgentChatTransferFlow.TransferMode.TRADE, "trash");

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotTransferRuntime.handleTransferCommand(entry, command, "show junk");

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, AgentChatTransferFlow.weirdTransferReply()));
        }
    }

    @Test
    void mesoTradeSchedulesAgentTransferDirectly() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);
        AgentChatTransferFlow.TransferCommand command =
                new AgentChatTransferFlow.TransferCommand(AgentChatTransferFlow.TransferMode.TRADE, "mesos");

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler =
                     mockStatic(AgentBotSchedulerRuntime.class);
             MockedStatic<AgentInventoryTransferService> inventory = mockStatic(AgentInventoryTransferService.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotTransferRuntime.handleTransferCommand(entry, command, "trade me mesos");

            inventory.verify(() -> AgentInventoryTransferService.startTradeTransfer("mesos", entry, bot));
        }
    }
}
