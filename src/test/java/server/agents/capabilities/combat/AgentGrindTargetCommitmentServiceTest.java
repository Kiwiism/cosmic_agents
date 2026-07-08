package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentGrindTargetStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentGrindTargetCommitmentServiceTest {
    @Test
    void commitsInitialTargetWhenNoReplacementExists() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Monster target = monsterAt(100, 100);
        AgentAttackPlan plan = mock(AgentAttackPlan.class);

        AgentGrindTargetCommitmentService.Result result =
                AgentGrindTargetCommitmentService.commitTarget(
                        entry,
                        agent,
                        new Point(0, 0),
                        target,
                        plan,
                        hooks(null, null));

        assertSame(target, result.target());
        assertSame(target.getPosition(), result.targetPosition());
        assertSame(plan, result.attackPlan());
        assertNull(result.rangedPriorityTarget());
        assertSame(target, AgentGrindTargetStateRuntime.target(entry));
    }

    @Test
    void rangedPriorityTargetReplacesTargetAndInvalidatesPlan() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Monster target = monsterAt(100, 100);
        Monster priority = monsterAt(200, 100);
        AgentAttackPlan plan = mock(AgentAttackPlan.class);

        AgentGrindTargetCommitmentService.Result result =
                AgentGrindTargetCommitmentService.commitTarget(
                        entry,
                        agent,
                        new Point(0, 0),
                        target,
                        plan,
                        hooks(priority, monsterAt(300, 100)));

        assertSame(priority, result.target());
        assertSame(priority.getPosition(), result.targetPosition());
        assertNull(result.attackPlan());
        assertSame(priority, result.rangedPriorityTarget());
        assertSame(priority, AgentGrindTargetStateRuntime.target(entry));
    }

    @Test
    void closerThreatReplacesTargetOnlyWhenNoRangedPriorityWasSelected() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Monster target = monsterAt(100, 100);
        Monster closerThreat = monsterAt(50, 100);
        AgentAttackPlan plan = mock(AgentAttackPlan.class);

        AgentGrindTargetCommitmentService.Result result =
                AgentGrindTargetCommitmentService.commitTarget(
                        entry,
                        agent,
                        new Point(0, 0),
                        target,
                        plan,
                        hooks(null, closerThreat));

        assertSame(closerThreat, result.target());
        assertSame(closerThreat.getPosition(), result.targetPosition());
        assertNull(result.attackPlan());
        assertNull(result.rangedPriorityTarget());
        assertSame(closerThreat, AgentGrindTargetStateRuntime.target(entry));
    }

    private static AgentGrindTargetCommitmentService.Hooks hooks(Monster rangedPriority, Monster closerThreat) {
        return new AgentGrindTargetCommitmentService.Hooks(
                (entry, agent, agentPosition, preferredTarget) -> rangedPriority,
                (agent, agentPosition, targetPosition) -> closerThreat);
    }

    private static Monster monsterAt(int x, int y) {
        Monster monster = mock(Monster.class);
        when(monster.getPosition()).thenReturn(new Point(x, y));
        return monster;
    }
}
