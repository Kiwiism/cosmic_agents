package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentGrindTargetSearchPolicyTest {
    @Test
    void keepsWalkingTowardReachableCommittedTarget() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Monster target = mock(Monster.class);
        when(target.getPosition()).thenReturn(new Point(300, 0));
        AgentAttackPlan plan = basicPlan(target);

        assertFalse(AgentGrindTargetSearchPolicy.shouldSearchForGrindTarget(
                entry, agent, target, plan, 1_000L, true));
        assertFalse(AgentGrindTargetSearchPolicy.shouldSearchForGrindTarget(
                entry, agent, target, null, 1_000L, true));
        assertTrue(AgentGrindTargetSearchPolicy.shouldSearchForGrindTarget(
                entry, agent, target, plan, 1_000L, false));
        assertTrue(AgentGrindTargetSearchPolicy.shouldSearchForGrindTarget(
                entry, agent, target, null, 1_000L, false));
    }

    private static AgentAttackPlan basicPlan(Monster target) {
        return new AgentAttackPlan(
                0, 0, 1, null, List.of(target), AgentAttackRoute.CLOSE,
                0, 0, 0, 0, 0, 0, 0, null);
    }
}
