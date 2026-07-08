package server.agents.integration;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotCombatRuntime;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotCombatRuntimeTest {
    @Test
    void combatBridgeDelegatesToBroadAgentRuntimes() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class);
             MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotCombatRuntime.sayMapNow(null, "combat");
            AgentBotCombatRuntime.afterDelay(500L, action);

            replies.verify(() -> AgentBotReplyRuntime.sayMapNow(null, "combat"));
            scheduler.verify(() -> AgentBotSchedulerRuntime.afterDelay(500L, action));
        }
    }
}
