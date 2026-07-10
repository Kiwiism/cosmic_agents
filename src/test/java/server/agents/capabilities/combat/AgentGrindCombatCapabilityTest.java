package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentGrindCombatCapabilityTest {
    @Test
    void returnsNullWhenRangedPriorityInputsAreMissing() {
        assertNull(AgentRangedPriorityTargetSelector.selectPriorityRangedAttackTarget(
                null,
                mock(Character.class),
                new Point(10, 20),
                mock(Monster.class)));
    }

    @Test
    void delegatesAoeRepositionToExistingPlanner() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);
        Monster target = mock(Monster.class);
        AgentAttackPlan attackPlan = mock(AgentAttackPlan.class);
        Point agentPosition = new Point(10, 20);
        Point anchor = new Point(30, 20);

        try (MockedStatic<AgentCombatAoeRepositionRuntime> planner =
                     mockStatic(AgentCombatAoeRepositionRuntime.class)) {
            planner.when(() -> AgentCombatAoeRepositionRuntime.aoeRepositionTarget(
                            entry, agent, target, attackPlan, AgentCombatConfig.cfg))
                    .thenReturn(anchor);

            assertEquals(anchor, AgentAoeRepositionService.resolveAoeReposition(
                    entry,
                    agent,
                    target,
                    attackPlan,
                    agentPosition));
        }
    }
}
