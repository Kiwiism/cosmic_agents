package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotSocialRuntime;
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
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler =
                     mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotSocialRuntime.socialCallbacks(entry).fame("Alice");

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(500), eq(900), any(Runnable.class)));
        }
    }

    @Test
    void fameCommandRepliesWhenTargetMissing() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            when(bot.getMap()).thenReturn(map);
            when(map.getCharacters()).thenReturn(List.of());

            AgentBotSocialRuntime.handleFameCommand(entry, "Alice");

            replies.verify(() -> AgentBotReplyRuntime.replyNow(eq(entry), contains("Alice")));
        }
    }
}
