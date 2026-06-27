package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotSocialReplyRuntime;
import server.agents.integration.AgentBotSocialRuntime;
import server.agents.integration.AgentBotSocialSchedulerRuntime;
import server.maps.MapleMap;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentBotSocialRuntimeTest {
    @Test
    void socialCallbackSchedulesFameCommand() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotSocialSchedulerRuntime> scheduler =
                     mockStatic(AgentBotSocialSchedulerRuntime.class)) {
            AgentBotSocialRuntime.socialCallbacks(entry).fame("Alice");

            scheduler.verify(() -> AgentBotSocialSchedulerRuntime.afterRandomDelay(eq(500), eq(900), any(Runnable.class)));
        }
    }

    @Test
    void fameCommandRepliesWhenTargetMissing() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        BotEntry entry = new BotEntry(bot, null, null);

        try (MockedStatic<AgentBotSocialReplyRuntime> replies = mockStatic(AgentBotSocialReplyRuntime.class)) {
            when(bot.getMap()).thenReturn(map);
            when(map.getCharacters()).thenReturn(List.of());

            AgentBotSocialRuntime.handleFameCommand(entry, "Alice");

            replies.verify(() -> AgentBotSocialReplyRuntime.replyNow(eq(entry), contains("Alice")));
        }
    }

    @Test
    void socialReplyAdapterDelegatesToAgentReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotSocialReplyRuntime.replyNow(entry, "reply");

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "reply"));
        }
    }

    @Test
    void socialSchedulerAdapterDelegatesToAgentSchedulerRuntime() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotSocialSchedulerRuntime.afterRandomDelay(500, 900, action);

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(500, 900, action));
        }
    }
}
