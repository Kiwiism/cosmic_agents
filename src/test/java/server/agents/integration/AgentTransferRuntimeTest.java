package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatTransferFlow;
import server.agents.capabilities.trade.AgentInventoryTransferService;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.integration.AgentTransferRuntime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentTransferRuntimeTest {
    @Test
    void weirdTransferRequestRepliesImmediately() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentChatTransferFlow.TransferCommand command =
                new AgentChatTransferFlow.TransferCommand(AgentChatTransferFlow.TransferMode.TRADE, "trash");

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            AgentTransferRuntime.handleTransferCommand(entry, command, "show junk");

            replies.verify(() -> AgentReplyRuntime.replyNow(entry, AgentChatTransferFlow.weirdTransferReply()));
        }
    }

    @Test
    void mesoTradeSchedulesAgentTransferDirectly() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentChatTransferFlow.TransferCommand command =
                new AgentChatTransferFlow.TransferCommand(AgentChatTransferFlow.TransferMode.TRADE, "mesos");

        try (MockedStatic<AgentSchedulerRuntime> scheduler =
                     mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentInventoryTransferService> inventory = mockStatic(AgentInventoryTransferService.class)) {
            scheduler.when(() -> AgentSchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentTransferRuntime.handleTransferCommand(entry, command, "trade me mesos");

            inventory.verify(() -> AgentInventoryTransferService.startTradeTransfer("mesos", entry, bot));
        }
    }
}
