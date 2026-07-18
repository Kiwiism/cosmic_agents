package net.server.services.task.channel;

import client.BotClient;
import client.Character;
import client.Client;
import net.server.channel.Channel;
import net.server.services.task.channel.OverallService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import net.packet.Packet;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentMonsterControlService;
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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class MobPhysicsServiceTest {
    private AgentMobReactionMode originalMode;
    private int originalAggroTimeoutMs;
    private boolean originalVirtualObserverStress;
    private MobPhysicsService service;

    @BeforeEach
    void setUp() {
        originalMode = AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE;
        originalAggroTimeoutMs = AgentCombatConfig.cfg.MOB_PHYSICS_AGGRO_TIMEOUT_MS;
        originalVirtualObserverStress = AgentCombatConfig.cfg.MOB_PHYSICS_VIRTUAL_OBSERVER_STRESS;
        AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE = AgentMobReactionMode.PHYSICS;
        AgentCombatConfig.cfg.MOB_PHYSICS_AGGRO_TIMEOUT_MS = 7_000;
        AgentCombatConfig.cfg.MOB_PHYSICS_VIRTUAL_OBSERVER_STRESS = false;
        service = new MobPhysicsService(false);
    }

    @AfterEach
    void tearDown() {
        service.dispose();
        AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE = originalMode;
        AgentCombatConfig.cfg.MOB_PHYSICS_AGGRO_TIMEOUT_MS = originalAggroTimeoutMs;
        AgentCombatConfig.cfg.MOB_PHYSICS_VIRTUAL_OBSERVER_STRESS = originalVirtualObserverStress;
    }

    @Test
    void acceptedHitAcquiresPublishesAndObserverLossReleasesWithoutLeak() {
        Fixture fixture = fixture(true, 10);
        assertTrue(service.acceptedHit(fixture.agent, fixture.monster, 10, 0));
        assertEquals(MobControlAuthority.AGENT_PHYSICS,
                fixture.monster.getControlAuthority());
        assertEquals(1, service.activeSessionCountForTest());

        service.tickForTest(System.nanoTime() + 100_000_000L);
        verify(fixture.map).broadcastMobPhysicsMessage(any(), any(Point.class));

        service.releaseMap(fixture.map, MobPhysicsService.ReleaseReason.OBSERVER_LOSS);
        assertEquals(0, service.activeSessionCountForTest());
        assertEquals(MobControlAuthority.NONE, fixture.monster.getControlAuthority());
    }

    @Test
    void legacyAgentTickCleanupCannotRevokePhysicsBeforeFirstPublication() {
        Fixture fixture = fixture(true, 1);
        assertTrue(service.acceptedHit(fixture.agent, fixture.monster, 10, 0));
        when(fixture.agent.getControlledMonsters()).thenReturn(List.of(fixture.monster));

        AgentMonsterControlService.releaseControlledMonsters(fixture.agent);

        assertEquals(MobControlAuthority.AGENT_PHYSICS,
                fixture.monster.getControlAuthority());
        assertEquals(1, service.activeSessionCountForTest());
        service.tickForTest(System.nanoTime() + 100_000_000L);
        verify(fixture.map).broadcastMobPhysicsMessage(any(), any(Point.class));
    }

    @Test
    void repeatedAgentHitsReuseSessionWithoutReversingActiveReaction() {
        Fixture fixture = fixture(true, 1);
        assertTrue(service.acceptedHit(fixture.agent, fixture.monster, 10, 100));
        MobSimulationSession first = service.sessionForTest(fixture.monster);
        assertNotNull(first);
        assertEquals(1, first.knockbackDirection());

        Character second = agent(fixture.map, new Point(100, 100));
        assertTrue(service.acceptedHit(second, fixture.monster, 10, 100));
        assertSame(first, service.sessionForTest(fixture.monster));
        assertSame(second, fixture.monster.getController());
        assertEquals(1, first.knockbackDirection(),
                "another source cannot reverse knockback before recovery completes");
        assertEquals(1, service.activeSessionCountForTest());
    }

    @Test
    void sevenSecondsWithoutAgentHitHandsBackToClientWithoutAggro() {
        Fixture fixture = fixture(true, 1);
        Channel channel = mock(Channel.class);
        OverallService overallService = mock(OverallService.class);
        when(fixture.map.getChannelServer()).thenReturn(channel);
        when(channel.getServiceAccess(any())).thenReturn(overallService);

        Character realPlayer = mock(Character.class);
        Client realClient = mock(Client.class);
        when(realPlayer.getClient()).thenReturn(realClient);
        when(realPlayer.getMap()).thenReturn(fixture.map);
        when(realPlayer.isLoggedinWorld()).thenReturn(true);
        when(realPlayer.isAlive()).thenReturn(true);
        when(fixture.map.getCharacters()).thenReturn(List.of(realPlayer));

        assertTrue(service.acceptedHit(fixture.agent, fixture.monster, 10, 0));
        MobSimulationSession session = service.sessionForTest(fixture.monster);
        long base = System.nanoTime() + 1_000_000_000L;
        session.acceptHit(fixture.agent, 10, 0, 1, base);

        service.tickForTest(base + 6_999_000_000L);
        assertEquals(1, service.activeSessionCountForTest());

        // Even if the current reaction blocks another knockback, this accepted hit renews
        // the lease. The session must survive until seven seconds after this newer hit.
        session.acceptHit(fixture.agent, 10, 0, 1, base + 6_999_000_000L);
        service.tickForTest(base + 13_997_000_000L);
        assertEquals(1, service.activeSessionCountForTest());

        service.tickForTest(base + 13_999_000_000L);
        assertEquals(0, service.activeSessionCountForTest());
        assertSame(realPlayer, fixture.monster.getController());
        assertEquals(MobControlAuthority.CLIENT, fixture.monster.getControlAuthority());
        assertFalse(fixture.monster.isControllerHasAggro());
        verify(realPlayer).controlMonster(fixture.monster);
    }

    @Test
    void staleInvalidationCannotReleaseARefreshedSession() {
        Fixture fixture = fixture(true, 1);
        assertTrue(service.acceptedHit(fixture.agent, fixture.monster, 10, 0));
        MobSimulationSession session = service.sessionForTest(fixture.monster);
        long staleGeneration = session.generation();

        Character second = agent(fixture.map, new Point(100, 100));
        assertTrue(service.acceptedHit(second, fixture.monster, 10, 0));
        service.releaseIfGeneration(
                session, MobPhysicsService.ReleaseReason.AGENT_DEPARTURE, staleGeneration);

        assertSame(session, service.sessionForTest(fixture.monster));
        assertSame(second, fixture.monster.getController());
        assertEquals(MobControlAuthority.AGENT_PHYSICS,
                fixture.monster.getControlAuthority());
    }

    @Test
    void agentOnlyMapDoesNotAcquireOrPublish() {
        Fixture fixture = fixture(false, 1);
        assertFalse(service.acceptedHit(fixture.agent, fixture.monster, 10, 0));
        assertEquals(0, service.activeSessionCountForTest());
        verify(fixture.map, never()).broadcastMobPhysicsMessage(any(), any(Point.class));
    }

    @Test
    void virtualObserverStressRunsProductionPhysicsWithoutFakeRecipient() {
        AgentCombatConfig.cfg.MOB_PHYSICS_VIRTUAL_OBSERVER_STRESS = true;
        Fixture fixture = fixture(false, 1);

        assertTrue(service.acceptedHit(fixture.agent, fixture.monster, 10, 0));
        service.tickForTest(System.nanoTime() + 100_000_000L);

        assertEquals(1, service.activeSessionCountForTest());
        verify(fixture.map).broadcastMobPhysicsMessage(any(), any(Point.class));
        assertTrue(MobPhysicsService.globalStatus().contains("virtualStress=true"));
        assertTrue(MobPhysicsService.globalStatus().contains("virtualSessions=1"));
    }

    @Test
    void disablingVirtualObserverStressReleasesUnobservedSessions() {
        AgentCombatConfig.cfg.MOB_PHYSICS_VIRTUAL_OBSERVER_STRESS = true;
        Fixture fixture = fixture(false, 1);
        assertTrue(service.acceptedHit(fixture.agent, fixture.monster, 10, 0));

        AgentCombatConfig.cfg.MOB_PHYSICS_VIRTUAL_OBSERVER_STRESS = false;
        service.tickForTest(System.nanoTime() + 100_000_000L);

        assertEquals(0, service.activeSessionCountForTest());
        assertEquals(MobControlAuthority.NONE, fixture.monster.getControlAuthority());
    }

    @Test
    void virtualObserverStressCannotBypassClientTransitionGuard() {
        AgentCombatConfig.cfg.MOB_PHYSICS_VIRTUAL_OBSERVER_STRESS = true;
        Fixture fixture = fixture(false, 1);
        when(fixture.map.hasTransitioningPlayerObserver()).thenReturn(true);

        assertFalse(service.acceptedHit(fixture.agent, fixture.monster, 10, 0));
        assertEquals(0, service.activeSessionCountForTest());
    }

    @Test
    void transitioningPlayerPreventsAcquisitionAndReleasesExistingSession() {
        Fixture fixture = fixture(true, 1);
        assertTrue(service.acceptedHit(fixture.agent, fixture.monster, 10, 0));

        when(fixture.map.hasTransitioningPlayerObserver()).thenReturn(true);
        service.tickForTest(System.nanoTime() + 100_000_000L);

        assertEquals(0, service.activeSessionCountForTest());
        assertEquals(MobControlAuthority.NONE, fixture.monster.getControlAuthority());
        assertFalse(service.acceptedHit(fixture.agent, fixture.monster, 10, 0));
    }

    @Test
    void stressOneHundredSessionsUsesOneBoundedServicePass() {
        MapleMap map = mock(MapleMap.class);
        when(map.isObservedByPlayer()).thenReturn(true);
        when(map.isMobPhysicsObserverWarmupComplete()).thenReturn(true);
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
        clearInvocations(map);
        long started = System.nanoTime();
        service.tickForTest(System.nanoTime() + 100_000_000L);
        long elapsed = System.nanoTime() - started;

        assertEquals(100, service.activeSessionCountForTest());
        verify(map).isObservedByPlayer();
        verify(map).hasTransitioningPlayerObserver();
        verify(map).isMobPhysicsObserverWarmupComplete();
        verify(map, times(100)).broadcastMobPhysicsMessage(any(), any(Point.class));
        assertTrue(elapsed < 5_000_000_000L, "100-session pass must remain bounded");
        service.releaseAll(MobPhysicsService.ReleaseReason.SERVICE_SHUTDOWN);
        assertEquals(0, service.activeSessionCountForTest());
    }

    @Test
    void stationaryPendingImpactSkipsRedundantMapVisibilityUpdate() {
        Fixture fixture = fixture(true, 1);
        assertTrue(service.acceptedHit(fixture.agent, fixture.monster, 10, 10_000));
        clearInvocations(fixture.map);

        service.tickForTest(System.nanoTime() + 100_000_000L);

        verify(fixture.map, never()).moveMonsterFromServerPhysics(any(), any(Point.class));
        verify(fixture.map).broadcastMobPhysicsMessage(any(), any(Point.class));
    }

    @Test
    void publishesOrdinaryMoveMonsterShapeAtBoundedCadence() {
        Fixture fixture = fixture(true, 1);
        assertTrue(service.acceptedHit(fixture.agent, fixture.monster, 10, 0));
        long first = System.nanoTime() + 100_000_000L;
        service.tickForTest(first);
        service.tickForTest(first + 10_000_000L);

        ArgumentCaptor<Packet> packets = ArgumentCaptor.forClass(Packet.class);
        verify(fixture.map).broadcastMobPhysicsMessage(packets.capture(), any(Point.class));
        byte[] bytes = packets.getValue().getBytes();
        assertEquals(bytes[29] & 1, bytes[8] & 0xff,
                "moving raw activity must select WZ move with matching facing");
        assertTrue((bytes[29] & 0xff) == 0 || (bytes[29] & 0xff) == 1,
                "moving knockback must publish the walk-animation fallback");

        service.tickForTest(first + 100_000_000L);
        verify(fixture.map, times(2)).broadcastMobPhysicsMessage(any(), any(Point.class));
    }

    @Test
    void walkingStanceFacesActualMovementDuringRetreat() {
        Fixture fixture = fixture(true, 100);
        assertTrue(service.acceptedHit(fixture.agent, fixture.monster, 10, 0));
        MobSimulationSession session = service.sessionForTest(fixture.monster);

        session.setMotion(server.life.simulation.MobMotionState.CHASE);
        session.body().setVelocity(-0.5, 0.0);
        assertEquals(1, MobPhysicsService.stance(session));

        session.body().setVelocity(0.5, 0.0);
        assertEquals(0, MobPhysicsService.stance(session));

        session.body().setVelocity(0.02, 0.0);
        assertEquals(0, MobPhysicsService.stance(session),
                "slow chase must still publish the walk animation");
    }

    @Test
    void knockbackAndFlinchPreserveFacingFromBeforeHit() {
        Fixture fixture = fixture(true, 1);
        fixture.monster.setStance(0);
        assertTrue(service.acceptedHit(fixture.agent, fixture.monster, 10, 0));
        MobSimulationSession session = service.sessionForTest(fixture.monster);
        assertEquals(1, session.knockbackDirection());
        session.setMotion(server.life.simulation.MobMotionState.KNOCKBACK);
        assertEquals(0, MobPhysicsService.stance(session));
        session.setMotion(server.life.simulation.MobMotionState.FLINCH);
        assertEquals(0, MobPhysicsService.stance(session),
                "stationary flinch must retain the walk-facing stance");
    }

    @Test
    void transitionReleaseConvertsTransientPhysicsStanceToStablePose() {
        Fixture grounded = fixture(true, 1);
        grounded.monster.setStance(1);
        assertTrue(service.acceptedHit(grounded.agent, grounded.monster, 10, 0));
        service.releaseMap(grounded.map, MobPhysicsService.ReleaseReason.CLIENT_MAP_TRANSITION);
        assertEquals(5, grounded.monster.getStance());

        Fixture rightFacing = fixture(true, 1);
        rightFacing.monster.setStance(0);
        assertTrue(service.acceptedHit(rightFacing.agent, rightFacing.monster, 10, 0));
        service.releaseMap(rightFacing.map, MobPhysicsService.ReleaseReason.CLIENT_MAP_TRANSITION);
        assertEquals(4, rightFacing.monster.getStance());
    }

    @Test
    void observerLossAlsoStabilizesTransientPhysicsStanceBeforeNextEntry() {
        Fixture fixture = fixture(true, 1);
        fixture.monster.setStance(0);
        assertTrue(service.acceptedHit(fixture.agent, fixture.monster, 10, 0));

        service.releaseMap(fixture.map, MobPhysicsService.ReleaseReason.OBSERVER_LOSS);

        assertEquals(4, fixture.monster.getStance());
        assertEquals(MobControlAuthority.NONE, fixture.monster.getControlAuthority());
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
        when(map.isMobPhysicsObserverWarmupComplete()).thenReturn(true);
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
