package server.agents.capabilities.combat;

import client.BotClient;
import client.Character;
import net.packet.Packet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import server.life.Monster;
import server.life.MonsterStats;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapleMap;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMobPhysicsProofServiceTest {
    private boolean originalEnabled;
    private boolean originalSynthetic;

    @BeforeEach
    void enableProofMode() {
        originalEnabled = AgentCombatConfig.cfg.MOB_PHYSICS_POC_ENABLED;
        originalSynthetic = AgentCombatConfig.cfg.SYNTHETIC_MOB_REACTION_ENABLED;
        AgentCombatConfig.cfg.MOB_PHYSICS_POC_ENABLED = true;
        AgentCombatConfig.cfg.SYNTHETIC_MOB_REACTION_ENABLED = false;
        AgentCombatConfig.cfg.MOB_PHYSICS_POC_KNOCKBACK_DISTANCE_X = 12;
        AgentCombatConfig.cfg.MOB_PHYSICS_POC_KNOCKBACK_DURATION_MS = 240;
        AgentCombatConfig.cfg.MOB_PHYSICS_POC_PUBLISH_INTERVAL_MS = 100;
        AgentCombatConfig.cfg.MOB_PHYSICS_POC_AGGRO_STEP_X = 4;
        AgentCombatConfig.cfg.MOB_PHYSICS_POC_STOP_DISTANCE_X = 24;
        AgentCombatConfig.cfg.MOB_PHYSICS_POC_RESUME_DISTANCE_X = 36;
        AgentMobPhysicsProofService.clearForTest();
    }

    @AfterEach
    void restoreMode() {
        AgentMobPhysicsProofService.clearForTest();
        AgentCombatConfig.cfg.MOB_PHYSICS_POC_ENABLED = originalEnabled;
        AgentCombatConfig.cfg.SYNTHETIC_MOB_REACTION_ENABLED = originalSynthetic;
    }

    @Test
    void acceptedHitAcquiresAgentPublishesKnockbackThenAggro() {
        Fixture fixture = fixture(new Point(-40, 99));

        AgentMobPhysicsProofService.acceptedHit(
                fixture.attacker, fixture.monster, 50, 125L, 1_000L, false);

        verify(fixture.monster).aggroAcquireAgentPhysicsController(fixture.attacker);
        AgentMobPhysicsProofService.tick(1_124L);
        verify(fixture.map, never()).broadcastMessage(any(Packet.class), any(Point.class));

        AgentMobPhysicsProofService.tick(1_125L);
        assertEquals(new Point(12, 99), fixture.monsterPosition.get());
        verify(fixture.monster).setStance(5);

        AgentMobPhysicsProofService.tick(1_364L);
        assertEquals(new Point(12, 99), fixture.monsterPosition.get());
        AgentMobPhysicsProofService.tick(1_365L);
        assertEquals(new Point(8, 99), fixture.monsterPosition.get());
        verify(fixture.monster).setStance(3);

        ArgumentCaptor<Packet> packets = ArgumentCaptor.forClass(Packet.class);
        verify(fixture.map, org.mockito.Mockito.times(2))
                .broadcastMessage(packets.capture(), any(Point.class));
        byte[] knockback = packets.getAllValues().get(0).getBytes();
        assertEquals(0xff, knockback[8] & 0xff);
        assertEquals(12, signedShort(knockback, 19));
        assertEquals(5, knockback[29] & 0xff);
        assertEquals(240, unsignedShort(knockback, 30));
        byte[] chase = packets.getAllValues().get(1).getBytes();
        assertEquals(8, signedShort(chase, 19));
        assertEquals(3, chase[29] & 0xff);
        assertEquals(100, unsignedShort(chase, 30));
    }

    @Test
    void repeatedHitRetargetsPendingImpactWithoutSecondSession() {
        Fixture fixture = fixture(new Point(-40, 99));
        AgentMobPhysicsProofService.acceptedHit(
                fixture.attacker, fixture.monster, 50, 100L, 1_000L, false);
        fixture.attackerPosition.set(new Point(40, 99));
        AgentMobPhysicsProofService.acceptedHit(
                fixture.attacker, fixture.monster, 50, 100L, 1_050L, false);

        verify(fixture.monster).aggroAcquireAgentPhysicsController(fixture.attacker);
        AgentMobPhysicsProofService.tick(1_149L);
        verify(fixture.map, never()).broadcastMessage(any(Packet.class), any(Point.class));
        AgentMobPhysicsProofService.tick(1_150L);

        assertEquals(new Point(-12, 99), fixture.monsterPosition.get());
        verify(fixture.monster).setStance(4);
    }

    @Test
    void disablingProofModeReleasesAgentAuthority() {
        Fixture fixture = fixture(new Point(-40, 99));
        AgentMobPhysicsProofService.acceptedHit(
                fixture.attacker, fixture.monster, 50, 0L, 1_000L, false);

        AgentCombatConfig.cfg.MOB_PHYSICS_POC_ENABLED = false;
        AgentMobPhysicsProofService.tick(1_000L);

        verify(fixture.monster).aggroReleaseAgentPhysicsController(fixture.attacker);
        verify(fixture.map, never()).broadcastMessage(any(Packet.class), any(Point.class));
    }

    @Test
    void unsupportedFlyingMobLeavesNativeControlUntouched() {
        Fixture fixture = fixture(new Point(-40, 99));
        when(fixture.stats.isFlying()).thenReturn(true);

        AgentMobPhysicsProofService.acceptedHit(
                fixture.attacker, fixture.monster, 50, 0L, 1_000L, false);

        verify(fixture.monster, never()).aggroAcquireAgentPhysicsController(any());
    }

    private static Fixture fixture(Point attackerStart) {
        MapleMap map = mock(MapleMap.class);
        when(map.isObservedByPlayer()).thenReturn(true);
        when(map.getMapArea()).thenReturn(new Rectangle(-500, -500, 1_000, 1_000));
        FootholdTree footholds = new FootholdTree(
                new Point(-500, -500), new Point(500, 500));
        footholds.insert(new Foothold(
                new Point(-500, 100), new Point(500, 100), 1));
        when(map.getFootholds()).thenReturn(footholds);

        AtomicReference<Point> attackerPosition = new AtomicReference<>(attackerStart);
        Character attacker = mock(Character.class);
        when(attacker.getMap()).thenReturn(map);
        when(attacker.getPosition()).thenAnswer(ignored -> attackerPosition.get());
        when(attacker.getClient()).thenReturn(mock(BotClient.class));
        when(attacker.isAlive()).thenReturn(true);

        MonsterStats stats = mock(MonsterStats.class);
        when(stats.getFixedStance()).thenReturn(0);
        when(stats.isFlying()).thenReturn(false);
        AtomicReference<Point> monsterPosition = new AtomicReference<>(new Point(0, 99));
        Monster monster = mock(Monster.class);
        when(monster.getObjectId()).thenReturn(100);
        when(monster.getMap()).thenReturn(map);
        when(monster.getPosition()).thenAnswer(ignored -> monsterPosition.get());
        when(monster.getStats()).thenReturn(stats);
        when(monster.isAlive()).thenReturn(true);
        when(monster.isMobile()).thenReturn(true);
        when(monster.getController()).thenReturn(attacker);
        when(monster.aggroAcquireAgentPhysicsController(attacker)).thenReturn(true);
        when(map.getMonsterByOid(100)).thenReturn(monster);
        doAnswer(invocation -> {
            monsterPosition.set(new Point(invocation.<Point>getArgument(1)));
            return null;
        }).when(map).moveMonster(eq(monster), any(Point.class));

        return new Fixture(map, attacker, monster, stats, attackerPosition, monsterPosition);
    }

    private static int signedShort(byte[] bytes, int offset) {
        return (short) unsignedShort(bytes, offset);
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private record Fixture(MapleMap map, Character attacker, Monster monster,
                           MonsterStats stats, AtomicReference<Point> attackerPosition,
                           AtomicReference<Point> monsterPosition) {
    }
}
