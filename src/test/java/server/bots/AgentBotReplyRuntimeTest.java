package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotReplyRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class AgentBotReplyRuntimeTest {
    @Test
    void immediateReplyDeliveryDelegatesToLegacyBotManager() {
        BotEntry entry = new BotEntry(null, null, null);
        BotManager manager = mock(BotManager.class);

        try (MockedStatic<BotManager> botManager = mockStatic(BotManager.class)) {
            botManager.when(BotManager::getInstance).thenReturn(manager);

            AgentBotReplyRuntime.replyNow(entry, "ok");
            AgentBotReplyRuntime.visibleSayNow(entry, "hello");

            verify(manager).botReply(entry, "ok");
            verify(manager).botVisibleSay(entry, "hello");
        }
    }

    @Test
    void immediatePartyDeliveryDelegatesToLegacyBotManager() {
        Character bot = mock(Character.class);
        BotManager manager = mock(BotManager.class);

        try (MockedStatic<BotManager> botManager = mockStatic(BotManager.class)) {
            botManager.when(BotManager::getInstance).thenReturn(manager);

            AgentBotReplyRuntime.sayPartyNow(bot, "party");

            verify(manager).botSayParty(bot, "party");
        }
    }
}
