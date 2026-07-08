package server.agents.capabilities.social;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentSchedulerRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentScrollReactionRuntimeTest {
    @Test
    void scrollReactionReplyAndSchedulerMethodsDelegateToBroadAgentRuntimes() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class);
             MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class)) {
            scheduler.when(() -> AgentSchedulerRuntime.randomDelayMs(0, 2001)).thenReturn(321L);

            AgentScrollReactionRuntime.queueSay(entry, "nice");
            AgentScrollReactionRuntime.afterDelay(123L, action);
            long delay = AgentScrollReactionRuntime.randomDelayMs(0, 2001);

            replies.verify(() -> AgentReplyRuntime.queueSay(entry, "nice"));
            scheduler.verify(() -> AgentSchedulerRuntime.afterDelay(123L, action));
            scheduler.verify(() -> AgentSchedulerRuntime.randomDelayMs(0, 2001));
            assertEquals(321L, delay);
        }
    }
}
