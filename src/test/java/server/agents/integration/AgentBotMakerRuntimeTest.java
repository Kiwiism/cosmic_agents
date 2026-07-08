package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotMakerRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentSchedulerRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotMakerRuntimeTest {
    @Test
    void makerBridgeMethodsDelegateToBroadAgentRuntimes() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class);
             MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class)) {
            AgentBotMakerRuntime.replyNow(entry, "reply");
            AgentBotMakerRuntime.afterDelay(5000L, action);
            AgentBotMakerRuntime.afterRandomDelay(900, 1100, action);

            replies.verify(() -> AgentReplyRuntime.replyNow(entry, "reply"));
            scheduler.verify(() -> AgentSchedulerRuntime.afterDelay(5000L, action));
            scheduler.verify(() -> AgentSchedulerRuntime.afterRandomDelay(900, 1100, action));
        }
    }
}
