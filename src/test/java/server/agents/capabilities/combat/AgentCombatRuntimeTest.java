package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentDialogueTransportRuntime;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentCombatRuntimeTest {
    @Test
    void combatBridgeDelegatesToBroadAgentRuntimes() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Runnable action = mock(Runnable.class);

        try (MockedStatic<AgentDialogueTransportRuntime> replies = mockStatic(AgentDialogueTransportRuntime.class);
             MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class)) {
            AgentCombatRuntime.sayMapNow(null, "combat");
            AgentCombatRuntime.afterDelay(entry, 500L, action);

            replies.verify(() -> AgentDialogueTransportRuntime.sayMapNow(null, "combat"));
            scheduler.verify(() -> AgentSchedulerRuntime.afterDelay(entry, 500L, action));
        }
    }
}
