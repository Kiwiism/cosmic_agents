package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotScrollReactionRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotScrollReactionRuntimeTest {
    @Test
    void scrollReactionReplyAndSchedulerMethodsDelegateToBroadAgentRuntimes() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class);
             MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.randomDelayMs(0, 2001)).thenReturn(321L);

            AgentBotScrollReactionRuntime.queueSay(entry, "nice");
            AgentBotScrollReactionRuntime.afterDelay(123L, action);
            long delay = AgentBotScrollReactionRuntime.randomDelayMs(0, 2001);

            replies.verify(() -> AgentBotReplyRuntime.queueSay(entry, "nice"));
            scheduler.verify(() -> AgentBotSchedulerRuntime.afterDelay(123L, action));
            scheduler.verify(() -> AgentBotSchedulerRuntime.randomDelayMs(0, 2001));
            assertEquals(321L, delay);
        }
    }
}
