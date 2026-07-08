package server.agents.integration;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotCombatRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentSchedulerRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotCombatRuntimeTest {
    @Test
    void combatBridgeDelegatesToBroadAgentRuntimes() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class);
             MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class)) {
            AgentBotCombatRuntime.sayMapNow(null, "combat");
            AgentBotCombatRuntime.afterDelay(500L, action);

            replies.verify(() -> AgentReplyRuntime.sayMapNow(null, "combat"));
            scheduler.verify(() -> AgentSchedulerRuntime.afterDelay(500L, action));
        }
    }
}
