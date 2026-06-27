package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatTransferFlow;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotTransferRuntime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class AgentBotTransferRuntimeTest {
    @Test
    void weirdTransferRequestRepliesImmediately() {
        BotEntry entry = new BotEntry(null, null, null);
        BotManager manager = mock(BotManager.class);
        AgentChatTransferFlow.TransferCommand command =
                new AgentChatTransferFlow.TransferCommand(AgentChatTransferFlow.TransferMode.TRADE, "trash");

        try (MockedStatic<BotManager> botManager = mockStatic(BotManager.class)) {
            botManager.when(BotManager::getInstance).thenReturn(manager);

            AgentBotTransferRuntime.handleTransferCommand(entry, command, "show junk");

            verify(manager).botReply(entry, AgentChatTransferFlow.weirdTransferReply());
        }
    }

    @Test
    void mesoTradeSchedulesLegacyTransferDirectly() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);
        AgentChatTransferFlow.TransferCommand command =
                new AgentChatTransferFlow.TransferCommand(AgentChatTransferFlow.TransferMode.TRADE, "mesos");

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class);
             MockedStatic<BotInventoryManager> inventory = mockStatic(BotInventoryManager.class)) {
            inventory.when(() -> BotInventoryManager.isMesoCategory("mesos")).thenReturn(true);
            scheduler.when(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        invocation.<Runnable>getArgument(2).run();
                        return null;
                    });

            AgentBotTransferRuntime.handleTransferCommand(entry, command, "trade me mesos");

            inventory.verify(() -> BotInventoryManager.startTradeTransfer("mesos", entry, bot));
        }
    }
}
