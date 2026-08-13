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
import static org.mockito.Mockito.mockStatic;
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
        AgentGrindTargetStateRuntime.commitTarget(entry, target, 1_000L, 2_500L);

        assertFalse(AgentGrindTargetSearchPolicy.shouldSearchForGrindTarget(
                entry, agent, target, plan, 1_000L, true));
        assertFalse(AgentGrindTargetSearchPolicy.shouldSearchForGrindTarget(
                entry, agent, target, null, 1_000L, true));
        assertTrue(AgentGrindTargetSearchPolicy.shouldSearchForGrindTarget(
                entry, agent, target, plan, 1_000L, false));
        assertTrue(AgentGrindTargetSearchPolicy.shouldSearchForGrindTarget(
                entry, agent, target, null, 1_000L, false));
        assertTrue(AgentGrindTargetSearchPolicy.shouldSearchForGrindTarget(
                entry, agent, target, plan, 3_500L, true));
        assertTrue(AgentGrindTargetSearchPolicy.shouldSearchForGrindTarget(
                entry, agent, target, null, 3_500L, true));
    }

    @Test
    void unreachableTargetBypassesCommitmentImmediately() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Monster target = mock(Monster.class);
        when(target.getPosition()).thenReturn(new Point(0, -200));
        AgentGrindTargetStateRuntime.commitTarget(entry, target, 1_000L, 4_000L);

        assertTrue(AgentGrindTargetSearchPolicy.shouldSearchForGrindTarget(
                entry, agent, target, null, 2_000L, false));
        assertTrue(AgentGrindTargetSearchPolicy.shouldSearchForGrindTarget(
                entry, agent, target, null, 5_000L, false));
    }

    @Test
    void localRequiredOpportunityBypassesRemoteCommitment() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Monster target = mock(Monster.class);
        AgentGrindTargetStateRuntime.commitTarget(entry, target, 1_000L, 4_000L);

        try (var targets = mockStatic(AgentCombatTargetRuntime.class)) {
            targets.when(() -> AgentCombatTargetRuntime.hasBetterLocalPreferredOpportunity(
                    entry, agent, target)).thenReturn(true);

            assertTrue(AgentGrindTargetSearchPolicy.shouldSearchForGrindTarget(
                    entry, agent, target, null, 2_000L, true));
        }
    }

    @Test
    void localityClassPreemptionDoesNotReintroduceRemoteThrashing() {
        assertTrue(AgentGrindTargetSearchPolicy.shouldPreemptCommittedTarget(true, 2, 1));
        assertTrue(AgentGrindTargetSearchPolicy.shouldPreemptCommittedTarget(true, 2, 0));
        assertFalse(AgentGrindTargetSearchPolicy.shouldPreemptCommittedTarget(true, 2, 2));
        assertFalse(AgentGrindTargetSearchPolicy.shouldPreemptCommittedTarget(true, 1, 1));
        assertTrue(AgentGrindTargetSearchPolicy.shouldPreemptCommittedTarget(false, 0, 0));
    }

    private static AgentAttackPlan basicPlan(Monster target) {
        return new AgentAttackPlan(
                0, 0, 1, null, List.of(target), AgentAttackRoute.CLOSE,
                0, 0, 0, 0, 0, 0, 0, null);
    }
}
