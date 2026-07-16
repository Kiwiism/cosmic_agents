package net.server.services.task.channel;

import client.BotClient;
import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import net.packet.Packet;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.mobcontrol.AgentMobReactionMode;
import server.life.Monster;
import server.life.MonsterStats;
import server.life.simulation.MobControlAuthority;
import server.life.simulation.MobSimulationSession;
import server.maps.MapleMap;
import server.physics.foothold.FootholdPhysicsIndex;
import server.physics.foothold.FootholdSegment;

import java.awt.Point;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class MobPhysicsServiceTest {
    private AgentMobReactionMode originalMode;
    private MobPhysicsService service;

    @BeforeEach
    void setUp() {
        originalMode = AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE;
        AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE = AgentMobReactionMode.PHYSICS;
        service = new MobPhysicsService(false);
    }

    @AfterEach
    void tearDown() {
        service.dispose();
        AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE = originalMode;
    }

    @Test
    void acceptedHitAcquiresPublishesAndObserverLossReleasesWithoutLeak() {
        Fixture fixture = fixture(true, 10);
        assertTrue(service.acceptedHit(fixture.agent, fixture.monster, 10, 0));
        assertEquals(MobControlAuthority.AGENT_PHYSICS,
                fixture.monster.getControlAuthority());
        assertEquals(1, service.activeSessionCountForTest());

        service.tickForTest(System.nanoTime() + 100_000_000L);
        verify(fixture.map).broadcastMessage(any(), any(Point.class));

        service.releaseMap(fixture.map, MobPhysicsService.ReleaseReason.OBSERVER_LOSS);
        assertEquals(0, service.activeSessionCountForTest());
        assertEquals(MobControlAuthority.NONE, fixture.monster.getControlAuthority());
    }

    @Test
    void repeatedAndDifferentAgentHitsReuseSessionAndSnapshotDirection() {
        Fixture fixture = fixture(true, 1);
        assertTrue(service.acceptedHit(fixture.agent, fixture.monster, 10, 100));
        MobSimulationSession first = service.sessionForTest(fixture.monster);
        assertNotNull(first);
        assertEquals(1, first.knockbackDirection());

        Character second = agent(fixture.map, new Point(100, 100));
        assertTrue(service.acceptedHit(second, fixture.monster, 10, 100));
        assertSame(first, service.sessionForTest(fixture.monster));
        assertSame(second, fixture.monster.getController());
        assertEquals(-1, first.knockbackDirection());
        assertEquals(1, service.activeSessionCountForTest());
    }

    @Test
    void agentOnlyMapDoesNotAcquireOrPublish() {
        Fixture fixture = fixture(false, 1);
        assertFalse(service.acceptedHit(fixture.agent, fixture.monster, 10, 0));
        assertEquals(0, service.activeSessionCountForTest());
        verify(fixture.map, never()).broadcastMessage(any(), any(Point.class));
    }

    @Test
    void stressOneHundredSessionsUsesOneBoundedServicePass() {
        MapleMap map = mock(MapleMap.class);
        when(map.isObservedByPlayer()).thenReturn(true);
        when(map.isSwim()).thenReturn(false);
        when(map.getCharacters()).thenReturn(List.of());
        when(map.getPhysicsTerrain()).thenReturn(new FootholdPhysicsIndex(List.of(
                new FootholdSegment(1, 0, 0, 1, 0, false,
                        -2000, 100, 2000, 100))));
        Map<Integer, Monster> monsters = new HashMap<>();
        when(map.getMonsterByOid(anyInt())).thenAnswer(
                invocation -> monsters.get(invocation.getArgument(0)));
        for (int i = 0; i < 100; i++) {
            MonsterStats stats = new MonsterStats();
            stats.setHp(100);
            stats.setRawSpeed(-20);
            stats.setPushed(1);
            stats.setPhysicsMobile(true);
            Monster monster = new Monster(100100, stats);
            monster.setObjectId(1000 + i);
            monster.setMap(map);
            monster.setPosition(new Point(i * 2, 100));
            monster.setFh(1);
            monsters.put(monster.getObjectId(), monster);
            assertTrue(service.acceptedHit(agent(map, new Point(-100, 100)), monster, 10, 0));
        }
        long started = System.nanoTime();
        service.tickForTest(System.nanoTime() + 100_000_000L);
        long elapsed = System.nanoTime() - started;

        assertEquals(100, service.activeSessionCountForTest());
        verify(map, times(100)).broadcastMessage(any(), any(Point.class));
        assertTrue(elapsed < 5_000_000_000L, "100-session pass must remain bounded");
        service.releaseAll(MobPhysicsService.ReleaseReason.SERVICE_SHUTDOWN);
        assertEquals(0, service.activeSessionCountForTest());
    }

    @Test
    void publishesOrdinaryMoveMonsterShapeAtBoundedCadence() {
        Fixture fixture = fixture(true, 1);
        assertTrue(service.acceptedHit(fixture.agent, fixture.monster, 10, 0));
        long first = System.nanoTime() + 100_000_000L;
        service.tickForTest(first);
        service.tickForTest(first + 10_000_000L);

        ArgumentCaptor<Packet> packets = ArgumentCaptor.forClass(Packet.class);
        verify(fixture.map).broadcastMessage(packets.capture(), any(Point.class));
        byte[] bytes = packets.getValue().getBytes();
        assertEquals(0xff, bytes[8] & 0xff, "raw activity must be ordinary/no skill");
        assertTrue((bytes[29] & 0xff) == 4 || (bytes[29] & 0xff) == 5,
                "flinch must publish a neutral stand stance");

        service.tickForTest(first + 100_000_000L);
        verify(fixture.map, times(2)).broadcastMessage(any(), any(Point.class));
    }

    @Test
    void liveModeChangeImmediatelyReleasesAllPhysicsAuthority() {
        Fixture fixture = fixture(true, 1);
        assertTrue(service.acceptedHit(fixture.agent, fixture.monster, 10, 0));

        assertEquals("OK: AGENT_MOB_REACTION_MODE = OFF",
                AgentCombatConfig.setConfigField("AGENT_MOB_REACTION_MODE", "off"));

        assertEquals(0, service.activeSessionCountForTest());
        assertEquals(MobControlAuthority.NONE, fixture.monster.getControlAuthority());
    }

    @Test
    void agentDeathAndMonsterDeathCannotLeakSessions() {
        Fixture agentDeath = fixture(true, 1);
        assertTrue(service.acceptedHit(agentDeath.agent, agentDeath.monster, 10, 0));
        when(agentDeath.agent.isAlive()).thenReturn(false);
        service.tickForTest(System.nanoTime() + 100_000_000L);
        assertEquals(0, service.activeSessionCountForTest());

        Fixture monsterDeath = fixture(true, 1);
        assertTrue(service.acceptedHit(monsterDeath.agent, monsterDeath.monster, 10, 0));
        monsterDeath.monster.setHpZero();
        service.tickForTest(System.nanoTime() + 100_000_000L);
        assertEquals(0, service.activeSessionCountForTest());
    }

    private static Fixture fixture(boolean observed, int pushed) {
        MapleMap map = mock(MapleMap.class);
        when(map.isObservedByPlayer()).thenReturn(observed);
        when(map.isSwim()).thenReturn(false);
        FootholdPhysicsIndex terrain = new FootholdPhysicsIndex(List.of(
                new FootholdSegment(1, 0, 0, 1, 0, false,
                        -500, 100, 500, 100)));
        when(map.getPhysicsTerrain()).thenReturn(terrain);
        when(map.getCharacters()).thenReturn(List.of());

        MonsterStats stats = new MonsterStats();
        stats.setHp(100);
        stats.setRawSpeed(-20);
        stats.setPushed(pushed);
        stats.setPhysicsMobile(true);
        Monster monster = new Monster(100100, stats);
        monster.setMap(map);
        monster.setPosition(new Point(0, 100));
        monster.setFh(1);
        when(map.getMonsterByOid(monster.getObjectId())).thenReturn(monster);
        Character agent = agent(map, new Point(-100, 100));
        return new Fixture(map, monster, agent);
    }

    private static Character agent(MapleMap map, Point position) {
        Character agent = mock(Character.class);
        when(agent.getClient()).thenReturn(mock(BotClient.class));
        when(agent.getMap()).thenReturn(map);
        when(agent.getPosition()).thenReturn(position);
        when(agent.isAlive()).thenReturn(true);
        return agent;
    }

    private record Fixture(MapleMap map, Monster monster, Character agent) {
    }
}
