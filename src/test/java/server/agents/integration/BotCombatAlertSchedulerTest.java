package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotCombatAlertRuntime;
import server.agents.integration.AgentBotCombatCooldownStateRuntime;
import server.agents.integration.AgentBotCombatRuntime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class BotCombatAlertSchedulerTest {
    @Test
    void markAlertedSchedulesResetThroughAgentCombatRuntime() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);

        try (MockedStatic<AgentBotCombatRuntime> scheduler = mockStatic(AgentBotCombatRuntime.class)) {
            AgentBotCombatAlertRuntime.markAlerted(entry);

            assertTrue(AgentBotCombatCooldownStateRuntime.alertResetScheduled(entry));
            scheduler.verify(() -> AgentBotCombatRuntime.afterDelay(
                    longThat(delay -> delay >= 50L && delay <= 5200L),
                    any(Runnable.class)));
        }
    }
}
