package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentSchedulerRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentCombatRuntimeTest {
    @Test
    void combatBridgeDelegatesToBroadAgentRuntimes() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class);
             MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class)) {
            AgentCombatRuntime.sayMapNow(null, "combat");
            AgentCombatRuntime.afterDelay(500L, action);

            replies.verify(() -> AgentReplyRuntime.sayMapNow(null, "combat"));
            scheduler.verify(() -> AgentSchedulerRuntime.afterDelay(500L, action));
        }
    }
}
