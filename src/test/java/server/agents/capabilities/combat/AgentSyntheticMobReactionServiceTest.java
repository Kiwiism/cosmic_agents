package server.agents.capabilities.combat;

import client.BotClient;
import client.Character;
import client.Client;
import net.packet.Packet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import server.agents.capabilities.mobcontrol.AgentMobReactionMode;
import server.life.Monster;
import server.life.MonsterStats;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapleMap;
import tools.Pair;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSyntheticMobReactionServiceTest {
    private AgentMobReactionMode startupReactionMode;

    @BeforeEach
    void enableReactionForBehaviorTests() {
        startupReactionMode = AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE;
        AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE = AgentMobReactionMode.SYNTHETIC;
    }

    @AfterEach
    void restoreConfiguredReactionMode() {
        AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE = startupReactionMode;
    }

    @Test
    void disabledReactionLeavesOriginalBehaviorUntouched() {
        AgentMobReactionMode originalMode = AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE;
        try {
            AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE = AgentMobReactionMode.OFF;
            Fixture fixture = fixture(new Point(-40, 99));
            List<ScheduledAction> scheduled = new ArrayList<>();

            AgentSyntheticMobReactionService.acceptedHit(
                    fixture.attacker, fixture.monster, 50, 125L,
                    (action, delayMs) -> scheduled.add(
                            new ScheduledAction(action, delayMs)));

            assertEquals(0, scheduled.size());
            verify(fixture.monster, never())
                    .aggroSuspendControllerForSyntheticReaction(anyLong());
            verify(fixture.map, never())
                    .broadcastMessage(any(Packet.class), any(Point.class));
        } finally {
            AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE = originalMode;
        }
    }

    @Test
    void liveDisableCancelsReactionWaitingOnAttackDelay() {
        AgentMobReactionMode originalMode = AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE;
        try {
            AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE = AgentMobReactionMode.SYNTHETIC;
            Fixture fixture = fixture(new Point(-40, 99));
            List<ScheduledAction> scheduled = new ArrayList<>();
            AgentSyntheticMobReactionService.acceptedHit(
                    fixture.attacker, fixture.monster, 50, 125L,
                    (action, delayMs) -> scheduled.add(
                            new ScheduledAction(action, delayMs)));

            AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE = AgentMobReactionMode.OFF;
            scheduled.get(0).action().run();

            verify(fixture.map, never())
                    .broadcastMessage(any(Packet.class), any(Point.class));
            verify(fixture.monster).aggroReleaseControllerAssignmentHold();
            verify(fixture.monster).aggroUpdateController(true);
            assertEquals(1, scheduled.size());
        } finally {
            AgentCombatConfig.cfg.AGENT_MOB_REACTION_MODE = originalMode;
        }
    }


    @Test
    void schedulesAcceptedHitAndBroadcastsRightwardHitReaction() {
        Fixture fixture = fixture(new Point(-40, 99));
        List<ScheduledAction> scheduled = new ArrayList<>();

        AgentSyntheticMobReactionService.acceptedHit(
                fixture.attacker, fixture.monster, 50, 125L,
                (action, delayMs) -> scheduled.add(
                        new ScheduledAction(action, delayMs)));

        assertEquals(1, scheduled.size());
        assertEquals(125L, scheduled.get(0).delayMs());
        assertNotNull(scheduled.get(0).action());
        verify(fixture.monster)
                .aggroSuspendControllerForSyntheticReaction(525L);

        scheduled.get(0).action().run();

        verify(fixture.monster).aggroCommitIfHeadless(eq(400L), any());
        verify(fixture.monster).setStance(5);
        ArgumentCaptor<Packet> packetCaptor = ArgumentCaptor.forClass(Packet.class);
        verify(fixture.map).broadcastMessage(
                packetCaptor.capture(), eq(new Point(0, 99)));
        byte[] packetBytes = packetCaptor.getValue().getBytes();
        assertEquals(9, packetBytes[8] & 0xff);
        assertEquals(0, packetBytes[18] & 0xff);
        assertEquals(12, (short) ((packetBytes[19] & 0xff)
                | (packetBytes[20] << 8)));
        assertEquals(101, (short) ((packetBytes[21] & 0xff)
                | (packetBytes[22] << 8)));
        assertEquals(0, (short) ((packetBytes[23] & 0xff)
                | (packetBytes[24] << 8)));
        assertEquals(0, (short) ((packetBytes[25] & 0xff)
                | (packetBytes[26] << 8)));
        assertEquals(5, packetBytes[29] & 0xff);
        assertEquals(240, (packetBytes[30] & 0xff) | (packetBytes[31] << 8));
        verify(fixture.map).moveMonster(fixture.monster, new Point(12, 99));
        verify(fixture.monster, never()).aggroUpdateController(true);
        assertEquals(2, scheduled.size());
        assertEquals(350L, scheduled.get(1).delayMs());

        scheduled.get(1).action().run();

        verify(fixture.monster).aggroReleaseControllerAssignmentHold();
        verify(fixture.monster).aggroUpdateController(true);
    }

    @Test
    void leftwardReactionUsesRightFacingHitState() {
        Fixture fixture = fixture(new Point(40, 99));
        List<ScheduledAction> scheduled = new ArrayList<>();

        AgentSyntheticMobReactionService.acceptedHit(
                fixture.attacker, fixture.monster, 50, 0L,
                (action, delayMs) -> scheduled.add(
                        new ScheduledAction(action, delayMs)));
        scheduled.get(0).action().run();
        scheduled.get(1).action().run();

        verify(fixture.monster).setStance(4);
        verify(fixture.map).moveMonster(fixture.monster, new Point(-12, 99));
        verify(fixture.monster).aggroUpdateController(true);
    }

    @Test
    void staleMonsterIdentityDropsDelayedReaction() {
        Fixture fixture = fixture(new Point(-40, 99));
        List<ScheduledAction> scheduled = new ArrayList<>();
        AgentSyntheticMobReactionService.acceptedHit(
                fixture.attacker, fixture.monster, 50, 0L,
                (action, delayMs) -> scheduled.add(
                        new ScheduledAction(action, delayMs)));
        when(fixture.map.getMonsterByOid(100)).thenReturn(mock(Monster.class));

        scheduled.get(0).action().run();

        verify(fixture.monster)
                .aggroSuspendControllerForSyntheticReaction(400L);
        verify(fixture.map, never()).broadcastMessage(any(Packet.class), any(Point.class));
        verify(fixture.monster, never()).aggroUpdateController(anyBoolean());
    }

    @Test
    void broadcastFailureStillRestoresNativeController() {
        Fixture fixture = fixture(new Point(-40, 99));
        List<ScheduledAction> scheduled = new ArrayList<>();
        AgentSyntheticMobReactionService.acceptedHit(
                fixture.attacker, fixture.monster, 50, 0L,
                (action, delayMs) -> scheduled.add(
                        new ScheduledAction(action, delayMs)));
        doThrow(new IllegalStateException("simulated broadcast failure"))
                .when(fixture.map).broadcastMessage(any(Packet.class), any(Point.class));

        assertThrows(IllegalStateException.class, scheduled.get(0).action()::run);

        verify(fixture.monster).aggroCommitIfHeadless(eq(400L), any());
        verify(fixture.monster).aggroReleaseControllerAssignmentHold();
        verify(fixture.monster).aggroUpdateController(true);
        verify(fixture.map, never()).moveMonster(any(), any());
    }

    @Test
    void unobservedOrImmobileMobDoesNotScheduleReaction() {
        Fixture fixture = fixture(new Point(-40, 99));
        when(fixture.monster.isMobile()).thenReturn(false);
        List<ScheduledAction> scheduled = new ArrayList<>();

        AgentSyntheticMobReactionService.acceptedHit(
                fixture.attacker, fixture.monster, 50, 0L,
                (action, delayMs) -> scheduled.add(
                        new ScheduledAction(action, delayMs)));

        assertEquals(0, scheduled.size());
        verify(fixture.monster, never())
                .aggroSuspendControllerForSyntheticReaction(anyLong());
    }

    @Test
    void realControllerIsSuspendedForSyntheticReaction() {
        Fixture fixture = fixture(new Point(-40, 99));
        Character realController = mock(Character.class);
        when(fixture.monster.getController()).thenReturn(realController);
        when(fixture.monster.aggroSuspendControllerForSyntheticReaction(anyLong()))
                .thenReturn(new Pair<>(realController, true));
        List<ScheduledAction> scheduled = new ArrayList<>();

        AgentSyntheticMobReactionService.acceptedHit(
                fixture.attacker, fixture.monster, 50, 0L,
                (action, delayMs) -> scheduled.add(
                        new ScheduledAction(action, delayMs)));

        assertEquals(1, scheduled.size());
        verify(fixture.monster)
                .aggroSuspendControllerForSyntheticReaction(400L);
    }

    @Test
    void realControllerTakingAuthorityDuringDelayCancelsSyntheticReaction() {
        Fixture fixture = fixture(new Point(-40, 99));
        List<ScheduledAction> scheduled = new ArrayList<>();
        AgentSyntheticMobReactionService.acceptedHit(
                fixture.attacker, fixture.monster, 50, 100L,
                (action, delayMs) -> scheduled.add(
                        new ScheduledAction(action, delayMs)));
        Character realController = mock(Character.class);
        when(fixture.monster.getController()).thenReturn(realController);
        doReturn(null).when(fixture.monster)
                .aggroCommitIfHeadless(anyLong(), any());

        scheduled.get(0).action().run();

        verify(fixture.monster)
                .aggroSuspendControllerForSyntheticReaction(500L);
        verify(fixture.monster).aggroCommitIfHeadless(eq(400L), any());
        verify(fixture.map, never()).broadcastMessage(any(Packet.class), any(Point.class));
        verify(fixture.map, never()).moveMonster(any(), any());
        verify(fixture.monster, never()).aggroUpdateController(anyBoolean());
        verify(fixture.monster).aggroReleaseControllerAssignmentHold();
        verify(fixture.monster).aggroAutoAggroUpdate(realController);
        assertEquals(1, scheduled.size());
    }

    @Test
    void overlappingHitsCoalesceUntilControlIsRestored() {
        Fixture fixture = fixture(new Point(-40, 99));
        List<ScheduledAction> scheduled = new ArrayList<>();
        AgentSyntheticMobReactionService.DelayedActionScheduler scheduler =
                (action, delayMs) -> scheduled.add(new ScheduledAction(action, delayMs));

        AgentSyntheticMobReactionService.acceptedHit(
                fixture.attacker, fixture.monster, 50, 100L, scheduler);
        AgentSyntheticMobReactionService.acceptedHit(
                fixture.attacker, fixture.monster, 50, 100L, scheduler);

        assertEquals(1, scheduled.size());
        verify(fixture.monster)
                .aggroSuspendControllerForSyntheticReaction(500L);

        scheduled.get(0).action().run();
        scheduled.get(1).action().run();
        verify(fixture.map).moveMonster(fixture.monster, new Point(12, 99));
    }

    @Test
    void keepsAcceptedHitDirectionWhenAttackerCrossesBeforeImpact() {
        Fixture fixture = fixture(new Point(-40, 99));
        List<ScheduledAction> scheduled = new ArrayList<>();

        AgentSyntheticMobReactionService.acceptedHit(
                fixture.attacker, fixture.monster, 50, 125L,
                (action, delayMs) -> scheduled.add(
                        new ScheduledAction(action, delayMs)));
        when(fixture.attacker.getPosition()).thenReturn(new Point(40, 99));

        scheduled.get(0).action().run();

        verify(fixture.monster).setStance(5);
        verify(fixture.map).moveMonster(fixture.monster, new Point(12, 99));
    }

    @Test
    void usesLiveConfiguredDistanceDurationAndControllerHold() {
        int originalDistance = AgentCombatConfig.cfg.SYNTHETIC_MOB_KNOCKBACK_DISTANCE_X;
        int originalDuration = AgentCombatConfig.cfg.SYNTHETIC_MOB_KNOCKBACK_DURATION_MS;
        int originalHold = AgentCombatConfig.cfg.SYNTHETIC_MOB_CONTROL_HOLD_MS;
        try {
            AgentCombatConfig.cfg.SYNTHETIC_MOB_KNOCKBACK_DISTANCE_X = 7;
            AgentCombatConfig.cfg.SYNTHETIC_MOB_KNOCKBACK_DURATION_MS = 222;
            AgentCombatConfig.cfg.SYNTHETIC_MOB_CONTROL_HOLD_MS = 333;
            Fixture fixture = fixture(new Point(-40, 99));
            List<ScheduledAction> scheduled = new ArrayList<>();

            AgentSyntheticMobReactionService.acceptedHit(
                    fixture.attacker, fixture.monster, 50, 0L,
                    (action, delayMs) -> scheduled.add(
                            new ScheduledAction(action, delayMs)));
            scheduled.get(0).action().run();

            ArgumentCaptor<Packet> packetCaptor = ArgumentCaptor.forClass(Packet.class);
            verify(fixture.map).broadcastMessage(
                    packetCaptor.capture(), eq(new Point(0, 99)));
            byte[] bytes = packetCaptor.getValue().getBytes();
            short endpointX = (short) ((bytes[19] & 0xff) | (bytes[20] << 8));
            short previousX = (short) ((bytes[23] & 0xff) | (bytes[24] << 8));
            int duration = (bytes[30] & 0xff) | (bytes[31] << 8);
            assertEquals(7, endpointX);
            assertEquals(0, previousX);
            assertEquals(222, duration);
            assertEquals(333L, scheduled.get(1).delayMs());
            verify(fixture.monster).aggroCommitIfHeadless(eq(383L), any());
            verify(fixture.map).moveMonster(fixture.monster, new Point(7, 99));
        } finally {
            AgentCombatConfig.cfg.SYNTHETIC_MOB_KNOCKBACK_DISTANCE_X = originalDistance;
            AgentCombatConfig.cfg.SYNTHETIC_MOB_KNOCKBACK_DURATION_MS = originalDuration;
            AgentCombatConfig.cfg.SYNTHETIC_MOB_CONTROL_HOLD_MS = originalHold;
        }
    }

    @Test
    void controllerHoldCannotExpireBeforeLongMovementFinishes() {
        int originalDuration = AgentCombatConfig.cfg.SYNTHETIC_MOB_KNOCKBACK_DURATION_MS;
        int originalHold = AgentCombatConfig.cfg.SYNTHETIC_MOB_CONTROL_HOLD_MS;
        try {
            AgentCombatConfig.cfg.SYNTHETIC_MOB_KNOCKBACK_DURATION_MS = 400;
            AgentCombatConfig.cfg.SYNTHETIC_MOB_CONTROL_HOLD_MS = 100;
            Fixture fixture = fixture(new Point(-40, 99));
            List<ScheduledAction> scheduled = new ArrayList<>();

            AgentSyntheticMobReactionService.acceptedHit(
                    fixture.attacker, fixture.monster, 50, 25L,
                    (action, delayMs) -> scheduled.add(
                            new ScheduledAction(action, delayMs)));

            verify(fixture.monster)
                    .aggroSuspendControllerForSyntheticReaction(525L);
            scheduled.get(0).action().run();
            verify(fixture.monster).aggroCommitIfHeadless(eq(500L), any());
            assertEquals(450L, scheduled.get(1).delayMs());
        } finally {
            AgentCombatConfig.cfg.SYNTHETIC_MOB_KNOCKBACK_DURATION_MS = originalDuration;
            AgentCombatConfig.cfg.SYNTHETIC_MOB_CONTROL_HOLD_MS = originalHold;
        }
    }

    @Test
    void blockedImpactStillBroadcastsStationaryFlinch() {
        Fixture fixture = fixture(new Point(-40, 99));
        when(fixture.map.getFootholds()).thenReturn(null);
        List<ScheduledAction> scheduled = new ArrayList<>();

        AgentSyntheticMobReactionService.acceptedHit(
                fixture.attacker, fixture.monster, 50, 0L,
                (action, delayMs) -> scheduled.add(
                        new ScheduledAction(action, delayMs)));
        scheduled.get(0).action().run();

        assertEquals(2, scheduled.size());
        verify(fixture.map).broadcastMessage(any(Packet.class), eq(new Point(0, 99)));
        verify(fixture.map).moveMonster(fixture.monster, new Point(0, 99));
        scheduled.get(1).action().run();
        verify(fixture.monster).aggroUpdateController(true);
    }

    @Test
    void ordinaryPlayerHitDoesNotEnterSyntheticReaction() {
        Fixture fixture = fixture(new Point(-40, 99));
        when(fixture.attacker.getClient()).thenReturn(mock(Client.class));
        List<ScheduledAction> scheduled = new ArrayList<>();

        AgentSyntheticMobReactionService.acceptedHit(
                fixture.attacker, fixture.monster, 50, 0L,
                (action, delayMs) -> scheduled.add(
                        new ScheduledAction(action, delayMs)));

        assertEquals(0, scheduled.size());
        verify(fixture.monster, never())
                .aggroSuspendControllerForSyntheticReaction(anyLong());
    }

    private static Fixture fixture(Point attackerPosition) {
        MapleMap map = mock(MapleMap.class);
        when(map.isObservedByPlayer()).thenReturn(true);
        when(map.getMapArea()).thenReturn(new Rectangle(-500, -500, 1_000, 1_000));
        FootholdTree footholds = new FootholdTree(
                new Point(-500, -500), new Point(500, 500));
        footholds.insert(new Foothold(
                new Point(-500, 100), new Point(500, 100), 1));
        when(map.getFootholds()).thenReturn(footholds);

        Character attacker = mock(Character.class);
        when(attacker.getMap()).thenReturn(map);
        when(attacker.getPosition()).thenReturn(attackerPosition);
        when(attacker.getClient()).thenReturn(mock(BotClient.class));

        MonsterStats stats = mock(MonsterStats.class);
        when(stats.getFixedStance()).thenReturn(0);
        when(stats.isFlying()).thenReturn(false);
        Monster monster = mock(Monster.class);
        when(monster.getObjectId()).thenReturn(100);
        when(monster.getMap()).thenReturn(map);
        when(monster.getPosition()).thenReturn(new Point(0, 99));
        when(monster.getStats()).thenReturn(stats);
        when(monster.isAlive()).thenReturn(true);
        when(monster.isMobile()).thenReturn(true);
        when(monster.aggroSuspendControllerForSyntheticReaction(anyLong()))
                .thenReturn(new Pair<>(attacker, true));
        when(monster.aggroRemoveControllerIfHeadless())
                .thenReturn(new Pair<>(null, false));
        when(monster.aggroCommitIfHeadless(anyLong(), any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return new Pair<>(attacker, true);
        });
        when(map.getMonsterByOid(100)).thenReturn(monster);
        return new Fixture(map, monster, attacker);
    }

    private record Fixture(MapleMap map, Monster monster, Character attacker) {
    }

    private record ScheduledAction(Runnable action, long delayMs) {
    }
}
