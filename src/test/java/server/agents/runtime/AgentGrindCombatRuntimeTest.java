package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.combat.AgentAttackPlan;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.integration.AgentBotCombatAoeRepositionRuntime;
import server.bots.BotEntry;
import server.life.Monster;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentGrindCombatRuntimeTest {
    @Test
    void returnsNullWhenRangedPriorityInputsAreMissing() {
        assertNull(AgentGrindCombatRuntime.selectPriorityRangedAttackTarget(
                null,
                mock(Character.class),
                new Point(10, 20),
                mock(Monster.class)));
    }

    @Test
    void delegatesAoeRepositionToExistingPlanner() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);
        Monster target = mock(Monster.class);
        AgentAttackPlan attackPlan = mock(AgentAttackPlan.class);
        Point agentPosition = new Point(10, 20);
        Point anchor = new Point(30, 20);

        try (MockedStatic<AgentBotCombatAoeRepositionRuntime> planner =
                     mockStatic(AgentBotCombatAoeRepositionRuntime.class)) {
            planner.when(() -> AgentBotCombatAoeRepositionRuntime.aoeRepositionTarget(
                            entry, agent, target, attackPlan, AgentCombatConfig.cfg))
                    .thenReturn(anchor);

            assertEquals(anchor, AgentGrindCombatRuntime.resolveAoeReposition(
                    entry,
                    agent,
                    target,
                    attackPlan,
                    agentPosition));
        }
    }
}
