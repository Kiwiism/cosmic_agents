package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotMakerRuntime;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotMakerRuntimeTest {
    @Test
    void makerBridgeMethodsDelegateToBroadAgentRuntimes() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class);
             MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotMakerRuntime.replyNow(entry, "reply");
            AgentBotMakerRuntime.afterDelay(5000L, action);
            AgentBotMakerRuntime.afterRandomDelay(900, 1100, action);

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "reply"));
            scheduler.verify(() -> AgentBotSchedulerRuntime.afterDelay(5000L, action));
            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(900, 1100, action));
        }
    }
}
