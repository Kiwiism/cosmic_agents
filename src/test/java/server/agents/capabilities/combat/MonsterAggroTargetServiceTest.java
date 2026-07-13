package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.integration.AgentPresence;
import server.life.Monster;
import server.maps.MapleMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonsterAggroTargetServiceTest {
    @AfterEach
    void resetAgentPresence() {
        AgentPresence.install(null);
    }

    @Test
    void recordsAndClearsLatestTarget() {
        MapleMap map = mock(MapleMap.class);
        Monster monster = mock(Monster.class);
        Character target = validTarget(11, "agent", map);
        Character controller = validTarget(22, "player", map);
        when(monster.getMap()).thenReturn(map);
        when(monster.getObjectId()).thenReturn(100);
        when(monster.isAlive()).thenReturn(true);
        when(map.getMonsterByOid(100)).thenReturn(monster);

        MonsterAggroTargetService.record(monster, target, controller,
                150, 100, "client-knockback-eligible", 1_000L);
        MonsterAggroTargetService.Snapshot snapshot =
                MonsterAggroTargetService.inspect(monster, 1_500L, 10_000L);

        assertTrue(snapshot.hasTarget());
        assertEquals(11, snapshot.targetId());
        assertEquals(22, snapshot.controllerId());
        assertEquals(150, snapshot.damage());

        MonsterAggroTargetService.prepareReaction(monster, target,
                150, 100, "client-knockback-eligible");
        MonsterAggroTargetService.clear(monster);
        assertFalse(MonsterAggroTargetService.inspect(monster, 1_500L, 10_000L).hasTarget());
        assertNull(MonsterAggroTargetService.consumePreparedReaction(monster, target));
    }

    @Test
    void expiresDisconnectedDeadTransitioningMapChangedAndTimedOutTargets() {
        MapleMap map = mock(MapleMap.class);
        MapleMap otherMap = mock(MapleMap.class);
        Monster monster = mock(Monster.class);
        when(monster.getMap()).thenReturn(map);
        when(monster.getObjectId()).thenReturn(100);
        when(monster.isAlive()).thenReturn(true);
        when(map.getMonsterByOid(100)).thenReturn(monster);

        Character disconnected = validTarget(1, "disconnected", map);
        when(disconnected.isLoggedinWorld()).thenReturn(false);
        assertInvalid(monster, disconnected, 1_000L, 1_001L, 10_000L);

        Character dead = validTarget(2, "dead", map);
        when(dead.isAlive()).thenReturn(false);
        assertInvalid(monster, dead, 2_000L, 2_001L, 10_000L);

        Character transitioning = validTarget(3, "transitioning", map);
        when(transitioning.isChangingMaps()).thenReturn(true);
        assertInvalid(monster, transitioning, 3_000L, 3_001L, 10_000L);

        Character moved = validTarget(4, "moved", otherMap);
        assertInvalid(monster, moved, 4_000L, 4_001L, 10_000L);

        Character timedOut = validTarget(5, "timed-out", map);
        MonsterAggroTargetService.record(monster, timedOut, timedOut,
                1, 1, "hurt-only", 5_000L);
        MonsterAggroTargetService.markUnreachable(monster, 5_000L, "no-route");
        assertFalse(MonsterAggroTargetService.inspect(monster, 6_001L, 1_000L).hasTarget());
    }

    @Test
    void serverPursuitStartsOnlyAfterAgentImpactDelayAndValidatesExactSpawn() {
        MapleMap map = mock(MapleMap.class);
        Monster monster = mock(Monster.class);
        Character target = validTarget(11, "agent", map);
        when(target.isLoggedinWorld()).thenReturn(false);
        AgentPresence.install(candidate -> candidate == target);
        when(monster.getMap()).thenReturn(map);
        when(monster.getObjectId()).thenReturn(100);
        when(monster.isAlive()).thenReturn(true);
        when(map.getMonsterByOid(100)).thenReturn(monster);

        MonsterAggroTargetService.record(monster, target, target, true,
                150, 100, "client-knockback-eligible", 1_000L, 400L);

        assertFalse(MonsterAggroTargetService.usesServerPursuit(monster, 1_399L));
        assertTrue(MonsterAggroTargetService.usesServerPursuit(monster, 1_400L));

        when(map.getMonsterByOid(100)).thenReturn(mock(Monster.class));
        assertFalse(MonsterAggroTargetService.usesServerPursuit(monster, 1_401L));
    }

    @Test
    void headlessAgentTargetRemainsValidWithoutPlayerLoginState() {
        MapleMap map = mock(MapleMap.class);
        Monster monster = mock(Monster.class);
        Character agent = validTarget(11, "headless-agent", map);
        when(agent.isLoggedinWorld()).thenReturn(false);
        when(monster.getMap()).thenReturn(map);
        when(monster.getObjectId()).thenReturn(100);
        when(monster.isAlive()).thenReturn(true);
        when(map.getMonsterByOid(100)).thenReturn(monster);
        AgentPresence.install(candidate -> candidate == agent);

        MonsterAggroTargetService.record(monster, agent, null, true,
                150, 100, "client-knockback-eligible", 1_000L, 400L);

        assertTrue(MonsterAggroTargetService.inspect(
                monster, 1_001L, 10_000L).hasTarget());
        assertEquals(1, MonsterAggroTargetService.activeTargets(
                1_001L, 10_000L).size());
    }

    @Test
    void removingHeadlessAgentInvalidatesTarget() {
        MapleMap map = mock(MapleMap.class);
        Monster monster = mock(Monster.class);
        Character agent = validTarget(11, "headless-agent", map);
        when(agent.isLoggedinWorld()).thenReturn(false);
        when(monster.getMap()).thenReturn(map);
        when(monster.getObjectId()).thenReturn(100);
        when(monster.isAlive()).thenReturn(true);
        when(map.getMonsterByOid(100)).thenReturn(monster);
        AgentPresence.install(candidate -> candidate == agent);
        MonsterAggroTargetService.record(monster, agent, null, true,
                150, 100, "client-knockback-eligible", 1_000L, 400L);

        AgentPresence.install(candidate -> false);

        assertFalse(MonsterAggroTargetService.inspect(
                monster, 1_001L, 10_000L).hasTarget());
    }

    @Test
    void acceptedClientKnockbackCommandIsRecordedWithoutApplyingDamage() {
        MapleMap map = mock(MapleMap.class);
        Monster monster = mock(Monster.class);
        Character target = validTarget(11, "agent", map);
        when(monster.getMap()).thenReturn(map);
        when(monster.getObjectId()).thenReturn(100);
        when(monster.isAlive()).thenReturn(true);
        when(map.getMonsterByOid(100)).thenReturn(monster);
        AgentPresence.install(candidate -> candidate == target);
        long appliedBefore = AgentMobReactionMetrics.snapshot().knockbackApplied();

        MonsterAggroTargetService.record(monster, target, target, true,
                150, 100, "client-knockback-eligible", 1_000L, 0L);
        MonsterAggroTargetService.recordControllerMovement(monster, 2, 1_200L);

        assertEquals(appliedBefore + 1,
                AgentMobReactionMetrics.snapshot().knockbackApplied());
        assertEquals("client-command-2", MonsterAggroTargetService.inspect(
                monster, 1_201L, 10_000L).latestMovement());
    }

    @Test
    void newHitSupersedesQueuedNativeControllerRestoration() {
        MapleMap map = mock(MapleMap.class);
        Monster monster = mock(Monster.class);
        Character target = validTarget(11, "agent", map);
        when(monster.getMap()).thenReturn(map);
        when(monster.getObjectId()).thenReturn(100);
        when(monster.isAlive()).thenReturn(true);
        when(map.getMonsterByOid(100)).thenReturn(monster);
        AgentPresence.install(candidate -> candidate == target);

        MonsterAggroTargetService.record(monster, target, target, true,
                100, 1, "hurt-only", 1_000L, 0L);
        when(target.isAlive()).thenReturn(false);
        assertTrue(MonsterAggroTargetService.activeTargets(1_001L, 10_000L).isEmpty());

        when(target.isAlive()).thenReturn(true);
        MonsterAggroTargetService.record(monster, target, target, true,
                100, 1, "hurt-only", 1_002L, 0L);

        assertTrue(MonsterAggroTargetService.drainExpiredTargets().isEmpty());
        MonsterAggroTargetService.clear(monster);
    }

    private static void assertInvalid(Monster monster, Character target,
                                      long recordedAt, long inspectedAt, long timeoutMs) {
        MonsterAggroTargetService.record(monster, target, target,
                1, 1, "hurt-only", recordedAt);
        assertFalse(MonsterAggroTargetService.inspect(monster, inspectedAt, timeoutMs).hasTarget());
    }

    private static Character validTarget(int id, String name, MapleMap map) {
        Character target = mock(Character.class);
        when(target.getId()).thenReturn(id);
        when(target.getName()).thenReturn(name);
        when(target.getMap()).thenReturn(map);
        when(target.isAlive()).thenReturn(true);
        when(target.isLoggedinWorld()).thenReturn(true);
        when(target.isChangingMaps()).thenReturn(false);
        return target;
    }
}
