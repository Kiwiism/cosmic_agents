package server.agents.capabilities.combat;

import client.Character;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.combat.AgentGrindTargetStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.CALLS_REAL_METHODS;

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
                        1_000L,
                        hooks(null, null));

        assertSame(target, result.target());
        assertSame(target.getPosition(), result.targetPosition());
        assertSame(plan, result.attackPlan());
        assertNull(result.rangedPriorityTarget());
        assertSame(target, AgentGrindTargetStateRuntime.target(entry));
        org.junit.jupiter.api.Assertions.assertTrue(
                AgentGrindTargetStateRuntime.committedTo(entry, target, 12_999L));
        org.junit.jupiter.api.Assertions.assertFalse(
                AgentGrindTargetStateRuntime.committedTo(entry, target, 13_000L));
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
                        1_000L,
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
                        1_000L,
                        hooks(null, closerThreat));

        assertSame(closerThreat, result.target());
        assertSame(closerThreat.getPosition(), result.targetPosition());
        assertNull(result.attackPlan());
        assertNull(result.rangedPriorityTarget());
        assertSame(closerThreat, AgentGrindTargetStateRuntime.target(entry));
    }

    @Test
    void activeCommitmentPreventsPerTickThreatReplacement() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Monster target = monsterAt(100, 100);
        Monster replacement = monsterAt(50, 100);
        AgentAttackPlan plan = mock(AgentAttackPlan.class);
        AgentGrindTargetStateRuntime.commitTarget(entry, target, 1_000L, 12_000L);

        AgentGrindTargetCommitmentService.Result result =
                AgentGrindTargetCommitmentService.commitTarget(
                        entry,
                        agent,
                        new Point(0, 0),
                        target,
                        plan,
                        2_000L,
                        hooks(replacement, replacement));

        assertSame(target, result.target());
        assertSame(plan, result.attackPlan());
        assertNull(result.rangedPriorityTarget());
    }

    @Test
    void progressivelyLengthensCommitmentAfterRepeatedTargetSwitches() {
        Character agent = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        Monster first = monsterAt(100, 100);
        Monster second = monsterAt(200, 100);
        Monster third = monsterAt(300, 100);
        Monster fourth = monsterAt(400, 100);

        AgentGrindTargetCommitmentService.commitTarget(
                entry, agent, new Point(), first, null, 0L, hooks(null, null));
        AgentGrindTargetCommitmentService.commitTarget(
                entry, agent, new Point(), second, null, 12_000L, hooks(null, null));
        assertSame(second, AgentGrindTargetStateRuntime.target(entry));
        org.junit.jupiter.api.Assertions.assertTrue(
                AgentGrindTargetStateRuntime.committedTo(entry, second, 31_999L));

        AgentGrindTargetCommitmentService.commitTarget(
                entry, agent, new Point(), third, null, 32_000L, hooks(null, null));
        org.junit.jupiter.api.Assertions.assertTrue(
                AgentGrindTargetStateRuntime.committedTo(entry, third, 66_999L));

        AgentGrindTargetCommitmentService.commitTarget(
                entry, agent, new Point(), fourth, null, 67_000L, hooks(null, null));
        org.junit.jupiter.api.Assertions.assertTrue(
                AgentGrindTargetStateRuntime.committedTo(entry, fourth, 126_999L));
        org.junit.jupiter.api.Assertions.assertEquals(3,
                AgentGrindTargetStateRuntime.targetSwitchCount(entry));
    }

    @Test
    void closerThreatIgnoresMobsOutsideTheActiveObjective() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(agent.getMap()).thenReturn(map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AgentCombatObjectiveTargetStateRuntime.setAllowedMobIds(entry, Set.of(100101));
        Monster disallowed = liveMonsterAt(120100, 20, 0);
        Monster allowed = liveMonsterAt(100101, 40, 0);
        when(map.getAllMonsters()).thenReturn(List.of(disallowed, allowed));

        try (var attacks = mockStatic(AgentAttackExecutionProvider.class, CALLS_REAL_METHODS)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(agent))
                    .thenReturn(WeaponType.BOW);

            assertSame(allowed, AgentAttackExecutionProvider.findCloserThreatMob(
                    entry, agent, new Point(0, 0), new Point(200, 0)));
        }
    }

    private static AgentGrindTargetCommitmentService.Hooks hooks(Monster rangedPriority, Monster closerThreat) {
        return new AgentGrindTargetCommitmentService.Hooks(
                (entry, agent, agentPosition, preferredTarget) -> rangedPriority,
                (entry, agent, agentPosition, targetPosition) -> closerThreat);
    }

    private static Monster monsterAt(int x, int y) {
        Monster monster = mock(Monster.class);
        when(monster.getPosition()).thenReturn(new Point(x, y));
        when(monster.isAlive()).thenReturn(true);
        return monster;
    }

    private static Monster liveMonsterAt(int mobId, int x, int y) {
        Monster monster = monsterAt(x, y);
        when(monster.getId()).thenReturn(mobId);
        when(monster.isAlive()).thenReturn(true);
        return monster;
    }
}
