package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotCombatReplyRuntime;
import server.agents.integration.AgentBotCombatRuntime;
import server.agents.integration.AgentBotCombatSchedulerRuntime;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotCombatRuntimeTest {
    @Test
    void combatBridgeDelegatesToCombatAdapters() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotCombatReplyRuntime> replies = mockStatic(AgentBotCombatReplyRuntime.class);
             MockedStatic<AgentBotCombatSchedulerRuntime> scheduler =
                     mockStatic(AgentBotCombatSchedulerRuntime.class)) {
            AgentBotCombatRuntime.sayMapNow(null, "combat");
            AgentBotCombatRuntime.afterDelay(500L, action);

            replies.verify(() -> AgentBotCombatReplyRuntime.sayMapNow(null, "combat"));
            scheduler.verify(() -> AgentBotCombatSchedulerRuntime.afterDelay(500L, action));
        }
    }

    @Test
    void combatReplyAdapterDelegatesToBroadReplyRuntime() {
        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotCombatReplyRuntime.sayMapNow(null, "combat");

            replies.verify(() -> AgentBotReplyRuntime.sayMapNow(null, "combat"));
        }
    }

    @Test
    void combatSchedulerAdapterDelegatesToBroadSchedulerRuntime() {
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotCombatSchedulerRuntime.afterDelay(500L, action);

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterDelay(500L, action));
        }
    }
}
