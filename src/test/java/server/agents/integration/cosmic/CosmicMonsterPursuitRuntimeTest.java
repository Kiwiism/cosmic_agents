package server.agents.integration.cosmic;

import client.Character;
import client.Client;
import config.YamlConfig;
import net.packet.Packet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import server.agents.capabilities.combat.MonsterAggroTargetService;
import server.agents.capabilities.combat.AcceptedMobHitResult;
import server.integration.AgentPresence;
import server.life.Monster;
import server.life.MonsterStats;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapleMap;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CosmicMonsterPursuitRuntimeTest {
    private boolean originalAggroEnabled;
    private boolean originalReactionEnabled;
    private long originalTimeout;

    @BeforeEach
    void enableFeature() {
        originalAggroEnabled = YamlConfig.config.agents.combat.lastHitAggro.enabled;
        originalReactionEnabled = YamlConfig.config.agents.combat.observedMobReaction.enabled;
        originalTimeout = YamlConfig.config.agents.combat.lastHitAggro.targetTimeoutMs;
        YamlConfig.config.agents.combat.lastHitAggro.enabled = true;
        YamlConfig.config.agents.combat.observedMobReaction.enabled = false;
        YamlConfig.config.agents.combat.lastHitAggro.targetTimeoutMs = 10_000L;
        ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
        CosmicMonsterPursuitRuntime.installLoopSchedulerForTest((action, period) -> scheduled);
    }

    @AfterEach
    void restoreGlobals() {
        YamlConfig.config.agents.combat.lastHitAggro.enabled = originalAggroEnabled;
        YamlConfig.config.agents.combat.observedMobReaction.enabled = originalReactionEnabled;
        YamlConfig.config.agents.combat.lastHitAggro.targetTimeoutMs = originalTimeout;
        AgentPresence.install(null);
        CosmicMonsterPursuitRuntime.resetForTest();
    }

    @Test
    void groundAgentTargetUsesOneValidServerMovementPacket() {
        Fixture fixture = fixture(false, 0, true);
        when(fixture.agent.getPosition()).thenReturn(new Point(20, 99));
        MonsterAggroTargetService.record(fixture.monster, fixture.agent, fixture.observer,
                true, 100, 1, "client-knockback-eligible", 1_000L, 0L);

        CosmicMonsterPursuitRuntime.tickNowForTest(1_001L);

        verify(fixture.monster).aggroRemoveController();
        ArgumentCaptor<Point> destination = ArgumentCaptor.forClass(Point.class);
        verify(fixture.map).moveMonster(eq(fixture.monster), destination.capture());
        assertTrue(destination.getValue().x < 0);
        assertEquals(99, destination.getValue().y);
        verify(fixture.map).broadcastMessage(any(Packet.class), any(Point.class));
    }

    @Test
    void acceptedImpactWaitsForDelayAndIsAppliedOnlyOnceBeforePursuit() {
        Fixture fixture = fixture(false, 0, true);
        when(fixture.agent.getPosition()).thenReturn(new Point(20, 99));
        AcceptedMobHitResult hit = new AcceptedMobHitResult(
                fixture.agent, true, 100, 100, true, false,
                true, 1, 200L, true, "client-knockback-eligible");
        MonsterAggroTargetService.record(fixture.monster, hit, fixture.observer,
                1, 1_000L, 500L);

        CosmicMonsterPursuitRuntime.tickNowForTest(1_199L);
        verify(fixture.map, never()).moveMonster(any(), any());

        CosmicMonsterPursuitRuntime.tickNowForTest(1_200L);
        CosmicMonsterPursuitRuntime.tickNowForTest(1_300L);

        verify(fixture.map, times(1)).moveMonster(eq(fixture.monster), any(Point.class));
        verify(fixture.map, times(1)).broadcastMessage(any(Packet.class), any(Point.class));
        assertEquals("server-proxy-knockback", MonsterAggroTargetService.inspect(
                fixture.monster, 1_301L, 10_000L).latestMovement());
    }

    @Test
    void reactionOnlyModeAppliesImpactThenRestoresNativeControl() {
        Fixture fixture = fixture(false, 0, true);
        when(fixture.agent.getPosition()).thenReturn(new Point(20, 99));
        YamlConfig.config.agents.combat.lastHitAggro.enabled = false;
        YamlConfig.config.agents.combat.observedMobReaction.enabled = true;
        AcceptedMobHitResult hit = new AcceptedMobHitResult(
                fixture.agent, true, 100, 100, true, false,
                true, 1, 0L, true, "client-knockback-eligible");
        MonsterAggroTargetService.record(fixture.monster, hit, fixture.observer,
                1, 1_000L, 300L);

        CosmicMonsterPursuitRuntime.tickNowForTest(1_001L);

        verify(fixture.map, times(1)).moveMonster(eq(fixture.monster), any(Point.class));
        verify(fixture.monster).aggroUpdateController();
        assertTrue(MonsterAggroTargetService.activeTargets(
                1_002L, 10_000L).isEmpty());
    }

    @Test
    void flyingAgentTargetMovesOnBothAxesWithinMapBounds() {
        Fixture fixture = fixture(true, 0, true);
        when(fixture.agent.getPosition()).thenReturn(new Point(100, 80));
        MonsterAggroTargetService.record(fixture.monster, fixture.agent, fixture.observer,
                true, 100, 1, "hurt-only", 1_000L, 0L);

        CosmicMonsterPursuitRuntime.tickNowForTest(1_001L);

        ArgumentCaptor<Point> destination = ArgumentCaptor.forClass(Point.class);
        verify(fixture.map).moveMonster(eq(fixture.monster), destination.capture());
        assertTrue(destination.getValue().x > 0);
        assertTrue(destination.getValue().y < 100);
    }

    @Test
    void fixedMonsterNeverReceivesProxyDisplacement() {
        Fixture fixture = fixture(false, 4, true);
        MonsterAggroTargetService.record(fixture.monster, fixture.agent, fixture.observer,
                true, 100, 1, "client-knockback-eligible", 1_000L, 0L);

        CosmicMonsterPursuitRuntime.tickNowForTest(1_001L);

        verify(fixture.map, never()).moveMonster(any(), any());
        verify(fixture.map, never()).broadcastMessage(any(Packet.class), any(Point.class));
    }

    @Test
    void latestPlayerTargetReturnsToNativePlayerController() {
        Fixture fixture = fixture(false, 0, true);
        MonsterAggroTargetService.record(fixture.monster, fixture.observer, fixture.observer,
                false, 100, 1, "accepted-damage", 1_000L, 0L);
        when(fixture.monster.getController()).thenReturn(null);

        CosmicMonsterPursuitRuntime.tickNowForTest(1_001L);

        verify(fixture.monster).aggroSwitchController(fixture.observer, true);
        verify(fixture.map, never()).moveMonster(any(), any());
    }

    @Test
    void observerLossStopsProxyAndClearsLogicalTarget() {
        Fixture fixture = fixture(false, 0, false);
        MonsterAggroTargetService.record(fixture.monster, fixture.agent, fixture.observer,
                true, 100, 1, "client-knockback-eligible", 1_000L, 0L);

        CosmicMonsterPursuitRuntime.tickNowForTest(1_001L);

        verify(fixture.monster).aggroRemoveController();
        assertTrue(MonsterAggroTargetService.activeTargets(1_002L, 10_000L).isEmpty());
        verify(fixture.map, never()).moveMonster(any(), any());
    }

    @Test
    void replacementMonsterWithSameOidDiscardsDelayedTarget() {
        Fixture fixture = fixture(false, 0, true);
        Monster replacement = mock(Monster.class);
        when(fixture.map.getMonsterByOid(100)).thenReturn(replacement);
        MonsterAggroTargetService.record(fixture.monster, fixture.agent, fixture.observer,
                true, 100, 1, "client-knockback-eligible", 1_000L, 500L);

        CosmicMonsterPursuitRuntime.tickNowForTest(2_000L);

        verify(fixture.map, never()).moveMonster(any(), any());
        verify(fixture.monster, never()).aggroRemoveController();
    }

    @Test
    void removedAgentClearsTargetAndRestoresNativeController() {
        Fixture fixture = fixture(false, 0, true);
        MonsterAggroTargetService.record(fixture.monster, fixture.agent, fixture.observer,
                true, 100, 1, "hurt-only", 1_000L, 0L);
        AgentPresence.install(candidate -> false);

        CosmicMonsterPursuitRuntime.tickNowForTest(1_001L);

        verify(fixture.monster).aggroUpdateController();
        verify(fixture.map, never()).moveMonster(any(), any());
        assertTrue(MonsterAggroTargetService.activeTargets(1_002L, 10_000L).isEmpty());
    }

    @Test
    void multipleObserversStillReceiveOneSharedMovementBroadcast() {
        Fixture fixture = fixture(false, 0, true);
        Character secondObserver = character(30, fixture.map, new Point(-200, 99));
        when(fixture.map.getAllPlayers()).thenReturn(
                List.of(fixture.agent, fixture.observer, secondObserver));
        MonsterAggroTargetService.record(fixture.monster, fixture.agent, fixture.observer,
                true, 100, 1, "hurt-only", 1_000L, 0L);

        CosmicMonsterPursuitRuntime.tickNowForTest(1_001L);

        verify(fixture.map, times(1)).broadcastMessage(any(Packet.class), any(Point.class));
        verify(fixture.map, times(1)).moveMonster(eq(fixture.monster), any(Point.class));
    }

    @Test
    void disablingFeatureStopsExistingProxyWithoutMovement() {
        Fixture fixture = fixture(false, 0, true);
        MonsterAggroTargetService.record(fixture.monster, fixture.agent, fixture.observer,
                true, 100, 1, "hurt-only", 1_000L, 0L);
        YamlConfig.config.agents.combat.lastHitAggro.enabled = false;

        CosmicMonsterPursuitRuntime.tickNowForTest(1_001L);

        verify(fixture.monster).aggroRemoveController();
        verify(fixture.map, never()).moveMonster(any(), any());
        assertTrue(MonsterAggroTargetService.activeTargets(1_002L, 10_000L).isEmpty());
    }

    @Test
    void centralizedSchedulerRegistersOnlyOneBoundedTask() {
        AtomicInteger registrations = new AtomicInteger();
        ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
        CosmicMonsterPursuitRuntime.installLoopSchedulerForTest((action, period) -> {
            assertEquals(CosmicMonsterPursuitRuntime.TICK_MS, period);
            registrations.incrementAndGet();
            return scheduled;
        });

        CosmicMonsterPursuitRuntime.ensureRunning();
        CosmicMonsterPursuitRuntime.ensureRunning();

        assertEquals(1, registrations.get());
    }

    private static Fixture fixture(boolean flying, int fixedStance, boolean observed) {
        MapleMap map = mock(MapleMap.class);
        when(map.isObservedByPlayer()).thenReturn(observed);
        when(map.getMapArea()).thenReturn(new Rectangle(-500, -500, 1_000, 1_000));
        FootholdTree tree = new FootholdTree(new Point(-500, -500), new Point(500, 500));
        Foothold foothold = new Foothold(new Point(-500, 100), new Point(500, 100), 1);
        tree.insert(foothold);
        when(map.getFootholds()).thenReturn(tree);

        Character agent = character(10, map, new Point(100, 99));
        when(agent.isLoggedinWorld()).thenReturn(false);
        Character observer = character(20, map, new Point(-100, 99));
        AgentPresence.install(candidate -> candidate == agent);
        when(map.getAllPlayers()).thenReturn(List.of(agent, observer));

        MonsterStats stats = mock(MonsterStats.class);
        when(stats.getFixedStance()).thenReturn(fixedStance);
        when(stats.getSpeed()).thenReturn(0);
        when(stats.isFlying()).thenReturn(flying);
        Monster monster = mock(Monster.class);
        when(monster.getObjectId()).thenReturn(100);
        when(monster.getMap()).thenReturn(map);
        when(monster.getStats()).thenReturn(stats);
        when(monster.getPosition()).thenReturn(new Point(0, 99));
        when(monster.getController()).thenReturn(observer);
        when(monster.getStance()).thenReturn(5);
        when(monster.isAlive()).thenReturn(true);
        when(monster.isMobile()).thenReturn(true);
        when(map.getMonsterByOid(100)).thenReturn(monster);
        return new Fixture(map, monster, agent, observer);
    }

    private static Character character(int id, MapleMap map, Point position) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn("character-" + id);
        when(character.getMap()).thenReturn(map);
        when(character.getPosition()).thenReturn(position);
        when(character.getClient()).thenReturn(mock(Client.class));
        when(character.isAlive()).thenReturn(true);
        when(character.isLoggedinWorld()).thenReturn(true);
        when(character.isChangingMaps()).thenReturn(false);
        return character;
    }

    private record Fixture(MapleMap map, Monster monster, Character agent,
                           Character observer) {
    }
}
