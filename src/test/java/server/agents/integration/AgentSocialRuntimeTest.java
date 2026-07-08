package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentSchedulerRuntime;
import server.agents.integration.AgentSocialRuntime;
import server.maps.MapleMap;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentSocialRuntimeTest {
    @Test
    void socialCallbackSchedulesFameCommand() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentSchedulerRuntime> scheduler =
                     mockStatic(AgentSchedulerRuntime.class)) {
            AgentSocialRuntime.socialCallbacks(entry).fame("Alice");

            scheduler.verify(() -> AgentSchedulerRuntime.afterRandomDelay(eq(500), eq(900), any(Runnable.class)));
        }
    }

    @Test
    void fameCommandRepliesWhenTargetMissing() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            when(bot.getMap()).thenReturn(map);
            when(map.getCharacters()).thenReturn(List.of());

            AgentSocialRuntime.handleFameCommand(entry, "Alice");

            replies.verify(() -> AgentReplyRuntime.replyNow(eq(entry), contains("Alice")));
        }
    }
}
