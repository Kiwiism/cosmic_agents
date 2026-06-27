package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotScrollReactionReplyRuntime;
import server.agents.integration.AgentBotScrollReactionRuntime;
import server.agents.integration.AgentBotScrollReactionSchedulerRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotScrollReactionRuntimeTest {
    @Test
    void scrollReactionReplyAndSchedulerMethodsDelegateToNarrowAdapters() {
        BotEntry entry = new BotEntry(null, null, null);
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotScrollReactionReplyRuntime> replies =
                     mockStatic(AgentBotScrollReactionReplyRuntime.class);
             MockedStatic<AgentBotScrollReactionSchedulerRuntime> scheduler =
                     mockStatic(AgentBotScrollReactionSchedulerRuntime.class)) {
            scheduler.when(() -> AgentBotScrollReactionSchedulerRuntime.randomDelayMs(0, 2001)).thenReturn(321L);

            AgentBotScrollReactionRuntime.queueSay(entry, "nice");
            AgentBotScrollReactionRuntime.afterDelay(123L, action);
            long delay = AgentBotScrollReactionRuntime.randomDelayMs(0, 2001);

            replies.verify(() -> AgentBotScrollReactionReplyRuntime.queueSay(entry, "nice"));
            scheduler.verify(() -> AgentBotScrollReactionSchedulerRuntime.afterDelay(123L, action));
            scheduler.verify(() -> AgentBotScrollReactionSchedulerRuntime.randomDelayMs(0, 2001));
            assertEquals(321L, delay);
        }
    }

    @Test
    void scrollReactionReplyAdapterDelegatesToAgentReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotScrollReactionReplyRuntime.queueSay(entry, "nice");

            replies.verify(() -> AgentBotReplyRuntime.queueSay(entry, "nice"));
        }
    }

    @Test
    void scrollReactionSchedulerAdapterDelegatesToAgentSchedulerRuntime() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            scheduler.when(() -> AgentBotSchedulerRuntime.randomDelayMs(0, 2001)).thenReturn(321L);

            AgentBotScrollReactionSchedulerRuntime.afterDelay(123L, action);
            long delay = AgentBotScrollReactionSchedulerRuntime.randomDelayMs(0, 2001);

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterDelay(123L, action));
            scheduler.verify(() -> AgentBotSchedulerRuntime.randomDelayMs(0, 2001));
            assertEquals(321L, delay);
        }
    }
}
