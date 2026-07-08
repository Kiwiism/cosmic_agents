package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentCombatAlertRuntimeTest {
    @Test
    void markAlertedSchedulesResetThroughAgentCombatRuntime() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);

        try (MockedStatic<AgentCombatRuntime> scheduler = mockStatic(AgentCombatRuntime.class)) {
            AgentCombatAlertRuntime.markAlerted(entry);

            assertTrue(AgentCombatCooldownStateRuntime.alertResetScheduled(entry));
            scheduler.verify(() -> AgentCombatRuntime.afterDelay(
                    longThat(delay -> delay >= 50L && delay <= 5200L),
                    any(Runnable.class)));
        }
    }
}
