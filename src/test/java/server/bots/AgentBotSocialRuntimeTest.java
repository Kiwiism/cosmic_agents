package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotSocialRuntime;
import server.maps.MapleMap;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentBotSocialRuntimeTest {
    @Test
    void socialCallbackSchedulesFameCommand() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotSocialRuntime.socialCallbacks(entry).fame("Alice");

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(500), eq(900), any(Runnable.class)));
        }
    }

    @Test
    void fameCommandRepliesWhenTargetMissing() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        BotEntry entry = new BotEntry(bot, null, null);
        BotManager manager = mock(BotManager.class);

        try (MockedStatic<BotManager> botManager = mockStatic(BotManager.class)) {
            botManager.when(BotManager::getInstance).thenReturn(manager);
            when(bot.getMap()).thenReturn(map);
            when(map.getCharacters()).thenReturn(List.of());

            AgentBotSocialRuntime.handleFameCommand(entry, "Alice");

            verify(manager).botReply(eq(entry), contains("Alice"));
        }
    }
}
