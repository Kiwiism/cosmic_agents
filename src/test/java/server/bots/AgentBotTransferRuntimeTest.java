package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatTransferFlow;
import server.agents.capabilities.trade.AgentInventoryTransferService;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotTransferReplyRuntime;
import server.agents.integration.AgentBotTransferRuntime;
import server.agents.integration.AgentBotTransferSchedulerRuntime;

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

        try (MockedStatic<AgentBotTransferReplyRuntime> replies = mockStatic(AgentBotTransferReplyRuntime.class)) {
            AgentBotTransferRuntime.handleTransferCommand(entry, command, "show junk");

            replies.verify(() -> AgentBotTransferReplyRuntime.replyNow(entry, AgentChatTransferFlow.weirdTransferReply()));
        }
    }

    @Test
    void mesoTradeSchedulesAgentTransferDirectly() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);
        AgentChatTransferFlow.TransferCommand command =
                new AgentChatTransferFlow.TransferCommand(AgentChatTransferFlow.TransferMode.TRADE, "mesos");

        try (MockedStatic<AgentBotTransferSchedulerRuntime> scheduler =
                     mockStatic(AgentBotTransferSchedulerRuntime.class);
             MockedStatic<AgentInventoryTransferService> inventory = mockStatic(AgentInventoryTransferService.class)) {
            scheduler.when(() -> AgentBotTransferSchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotTransferRuntime.handleTransferCommand(entry, command, "trade me mesos");

            inventory.verify(() -> AgentInventoryTransferService.startTradeTransfer("mesos", entry, bot));
        }
    }

    @Test
    void transferReplyAdapterDelegatesToAgentReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotTransferReplyRuntime.replyNow(entry, "reply");

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "reply"));
        }
    }

    @Test
    void transferSchedulerAdapterDelegatesToAgentSchedulerRuntime() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.randomDelayMs(500, 700)).thenReturn(600L);

            AgentBotTransferSchedulerRuntime.afterDelay(200L, action);
            AgentBotTransferSchedulerRuntime.afterRandomDelay(500, 700, action);
            long delay = AgentBotTransferSchedulerRuntime.randomDelayMs(500, 700);

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterDelay(200L, action));
            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(500, 700, action));
            scheduler.verify(() -> AgentBotSchedulerRuntime.randomDelayMs(500, 700));
            org.junit.jupiter.api.Assertions.assertEquals(600L, delay);
        }
    }
}
