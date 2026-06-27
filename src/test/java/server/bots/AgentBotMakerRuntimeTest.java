package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotMakerReplyRuntime;
import server.agents.integration.AgentBotMakerRuntime;
import server.agents.integration.AgentBotMakerSchedulerRuntime;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotMakerRuntimeTest {
    @Test
    void makerBridgeMethodsDelegateToMakerAdapters() {
        BotEntry entry = new BotEntry(null, null, null);
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotMakerReplyRuntime> replies = mockStatic(AgentBotMakerReplyRuntime.class);
             MockedStatic<AgentBotMakerSchedulerRuntime> scheduler =
                     mockStatic(AgentBotMakerSchedulerRuntime.class)) {
            AgentBotMakerRuntime.replyNow(entry, "reply");
            AgentBotMakerRuntime.afterDelay(5000L, action);
            AgentBotMakerRuntime.afterRandomDelay(900, 1100, action);

            replies.verify(() -> AgentBotMakerReplyRuntime.replyNow(entry, "reply"));
            scheduler.verify(() -> AgentBotMakerSchedulerRuntime.afterDelay(5000L, action));
            scheduler.verify(() -> AgentBotMakerSchedulerRuntime.afterRandomDelay(900, 1100, action));
        }
    }

    @Test
    void makerReplyAdapterDelegatesToBroadReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotMakerReplyRuntime.replyNow(entry, "reply");

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "reply"));
        }
    }

    @Test
    void makerSchedulerAdapterDelegatesToBroadSchedulerRuntime() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotMakerSchedulerRuntime.afterDelay(5000L, action);
            AgentBotMakerSchedulerRuntime.afterRandomDelay(900, 1100, action);

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterDelay(5000L, action));
            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(900, 1100, action));
        }
    }
}
