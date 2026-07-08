package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentSchedulerRuntime;
import server.agents.integration.AgentBotScrollReactionRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotScrollReactionRuntimeTest {
    @Test
    void scrollReactionReplyAndSchedulerMethodsDelegateToBroadAgentRuntimes() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class);
             MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class)) {
            scheduler.when(() -> AgentSchedulerRuntime.randomDelayMs(0, 2001)).thenReturn(321L);

            AgentBotScrollReactionRuntime.queueSay(entry, "nice");
            AgentBotScrollReactionRuntime.afterDelay(123L, action);
            long delay = AgentBotScrollReactionRuntime.randomDelayMs(0, 2001);

            replies.verify(() -> AgentReplyRuntime.queueSay(entry, "nice"));
            scheduler.verify(() -> AgentSchedulerRuntime.afterDelay(123L, action));
            scheduler.verify(() -> AgentSchedulerRuntime.randomDelayMs(0, 2001));
            assertEquals(321L, delay);
        }
    }
}
