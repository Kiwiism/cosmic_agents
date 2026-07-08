package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import server.agents.integration.AgentFidgetStateRuntime;

import server.agents.integration.AgentClimbStateRuntime;

import server.agents.integration.AgentMovementPhysicsStateRuntime;



import server.agents.integration.AgentMovementStateRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.movement.AgentAirborneMovementService;
import server.agents.capabilities.movement.AgentClimbMovementService;
import server.agents.capabilities.movement.AgentGroundCollisionService;
import server.agents.capabilities.movement.AgentGroundMovementPolicy;
import server.agents.capabilities.movement.AgentGroundMovementService;
import server.agents.capabilities.movement.AgentGroundMovementRuntimeService;
import server.agents.capabilities.movement.AgentGroundTargetService;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.capabilities.movement.AgentMovementProfileService;
import server.agents.capabilities.movement.AgentMovementRecoveryService;

import server.agents.capabilities.navigation.AgentNavigationGraph;

import server.agents.capabilities.movement.AgentMovementProfile;

import client.Character;
import constants.game.CharacterStance;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.fidget.AgentFidgetService;
import server.agents.capabilities.movement.fidget.AgentFidgetMode;
import server.agents.capabilities.movement.fidget.AgentFidgetTrigger;
import server.agents.integration.AgentNavigationDebugStateRuntime;
import server.agents.integration.AgentMoveTargetStateRuntime;
import server.agents.integration.AgentOwnerMotionStateRuntime;
import server.life.Monster;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class BotMovementManagerTest {
    @Test
    void shouldClampGrindingTargetAwayFromCurrentFootholdEdgeForSameFootholdCombat() {
        MapleMap map = new MapleMap(910000007, 0, 0, 910000007, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        Foothold foothold = new Foothold(new Point(0, 100), new Point(200, 100), 1);
        footholds.insert(foothold);
        map.setFootholds(footholds);
        AgentNavigationGraphService.rebuildGraph(map);

        Character bot = mockBot(new Point(100, 100), map);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setGrinding(entry, true);

        Point adjusted = AgentGroundTargetService.adjustGrindingTargetPosition(entry, foothold, new Point(190, 100));

        assertEquals(new Point(160, 100), adjusted);
    }

    @Test
    void shouldNotClampGrindingTargetWhenTargetIsOnDifferentFoothold() {
        MapleMap map = new MapleMap(910000008, 0, 0, 910000008, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        Foothold leftFoothold = new Foothold(new Point(-200, 100), new Point(0, 100), 1);
        Foothold rightFoothold = new Foothold(new Point(1, 100), new Point(200, 100), 2);
        footholds.insert(leftFoothold);
        footholds.insert(rightFoothold);
        map.setFootholds(footholds);
        AgentNavigationGraphService.rebuildGraph(map);

        Character bot = mockBot(new Point(-100, 100), map);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setGrinding(entry, true);

        Point targetPos = new Point(190, 100);
        Point adjusted = AgentGroundTargetService.adjustGrindingTargetPosition(entry, leftFoothold, targetPos);

        assertEquals(targetPos, adjusted);
    }

    @Test
    void shouldClampGrindingTargetAcrossEntireCurrentRegionInsteadOfSingleFoothold() {
        MapleMap map = new MapleMap(910000023, 0, 0, 910000023, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        Foothold leftFoothold = new Foothold(new Point(-200, 100), new Point(0, 100), 1);
        Foothold rightFoothold = new Foothold(new Point(0, 100), new Point(200, 100), 2);
        leftFoothold.setNext(2);
        rightFoothold.setPrev(1);
        footholds.insert(leftFoothold);
        footholds.insert(rightFoothold);
        map.setFootholds(footholds);
        AgentNavigationGraphService.rebuildGraph(map);

        Character bot = mockBot(new Point(-150, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setGrinding(entry, true);

        Point adjusted = AgentGroundTargetService.adjustGrindingTargetPosition(entry, leftFoothold, new Point(190, 100));

        assertEquals(new Point(160, 100), adjusted);
    }

    @Test
    void shouldNotClampGrindingTargetForSmallRegion() {
        MapleMap map = new MapleMap(910000024, 0, 0, 910000024, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        Foothold foothold = new Foothold(new Point(0, 100), new Point(60, 100), 1);
        footholds.insert(foothold);
        map.setFootholds(footholds);
        AgentNavigationGraphService.rebuildGraph(map);

        Character bot = mockBot(new Point(20, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setGrinding(entry, true);

        Point targetPos = new Point(55, 100);
        Point adjusted = AgentGroundTargetService.adjustGrindingTargetPosition(entry, foothold, targetPos);

        assertEquals(targetPos, adjusted);
    }

    @Test
    void shouldNotAirSteerCommittedRopeExitClimbArc() {
        MapleMap map = new MapleMap(910000025, 0, 0, 910000025, 1.0f);
        map.setFootholds(new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000)));
        Character bot = mockBot(new Point(0, 0), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setInAir(entry, true);
        AgentMovementPhysicsStateRuntime.setAirVelocityX(entry, -8);
        AgentMovementPhysicsStateRuntime.setPhysicsX(entry, 0);
        AgentMovementPhysicsStateRuntime.setPhysicsY(entry, 0);
        AgentMovementPhysicsStateRuntime.setVerticalVelocity(entry, -10f);
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, new AgentNavigationGraph.Edge(
                25, 14, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(-437, -181), new Point(-473, -211),
                -8, 0, -437, -1471, 84, 250
        ));

        AgentAirborneMovementService.tickAirborne(entry, new Point(300, 0));

        assertEquals(0.0, AgentMovementPhysicsStateRuntime.airSteerVelocityX(entry));
    }

    @Test
    void shouldNotHoldClimbIdleWhileCommittedClimbEdgeIsActive() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, new AgentNavigationGraph.Edge(
                1, 2, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(0, 0), new Point(0, -100),
                0, 0, 10, -100, 40, 100
        ));

        assertFalse(AgentClimbMovementService.shouldHoldClimbIdle(entry, 0, 0));
    }

    @Test
    void shouldAllowIdleClimbHoldWithoutCommittedClimbEdge() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertTrue(AgentClimbMovementService.shouldHoldClimbIdle(entry, 0, 0));
    }

    @Test
    void shouldAllowWalkingAlongUpperPlatformWhenExactGroundProbeWouldHitLowerPlatform() {
        MapleMap map = mock(MapleMap.class);
        when(map.getPointBelow(any(Point.class))).thenAnswer(invocation -> {
            Point probe = invocation.getArgument(0);
            if (probe.y >= 151) {
                return new Point(probe.x, 215);
            }
            return new Point(probe.x, 150);
        });
        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(map);

        assertTrue(AgentGroundCollisionService.canWalkGroundStep(map, new Point(-73, 151), 8));
    }

    @Test
    void shouldHoldCommittedClimbEdgeWhenAlreadyAtAnchorY() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(668, 1757));
        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getMap()).thenReturn(map);
        when(bot.getId()).thenReturn(1);
        when(bot.getHp()).thenReturn(100);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentClimbStateRuntime.setClimbingOnRope(entry, new Rope(668, 1727, 1980, false));
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, new AgentNavigationGraph.Edge(
                68, 54, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(668, 1757), new Point(796, 2025),
                8, 0, 668, 1727, 1980, 650
        ));

        AgentClimbMovementService.tickClimbing(entry, new Point(668, 1757), false);

        assertEquals(new Point(668, 1757), bot.getPosition());
    }

    @Test
    void shouldSnapCommittedClimbEdgeWhenAnchorIsWithinSingleClimbStep() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(-437, -1142));
        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getMap()).thenReturn(map);
        when(bot.getId()).thenReturn(1);
        when(bot.getHp()).thenReturn(100);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentClimbStateRuntime.setClimbingOnRope(entry, new Rope(-437, -1471, 84, false));
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, new AgentNavigationGraph.Edge(
                25, 2, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(-437, -1141), new Point(-477, -1166),
                -8, 0, -437, -1471, 84, 250
        ));
        AgentNavigationDebugStateRuntime.setNavPreciseTarget(entry, true);

        AgentClimbMovementService.tickClimbing(entry, new Point(-437, -1141), true);

        assertEquals(new Point(-437, -1141), bot.getPosition(),
                "climb movement should snap to a precise anchor that is closer than one climb step");
    }

    @Test
    void shouldNotSnapPreciseClimbTargetOutsideRopeSpan() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentClimbStateRuntime.setClimbingOnRope(entry, new Rope(3398, 126, 332, false));
        AgentNavigationDebugStateRuntime.setNavPreciseTarget(entry, true);

        // Above the rope (y <= topY) and strictly below it (y > bottomY) must reject snap.
        // Snap AT bottomY is allowed for rope-exit launch anchors authored at the rope bottom
        // (pathlog-Leroy/John); see shouldSnapCommittedClimbEdgeAtRopeBottomYAnchor.
        assertFalse(AgentClimbMovementService.shouldSnapToClimbTarget(entry, new Point(3398, 124), -2));
        assertFalse(AgentClimbMovementService.shouldSnapToClimbTarget(entry, new Point(3398, 333), 1));
    }

    @Test
    void shouldSnapCommittedClimbEdgeAtRopeBottomYAnchor() {
        // pathlog-Leroy-2026-05-10T025338, pathlog-John-2026-05-10T050253:
        // CLIMB exit edge with launchStepX != 0 and startPoint.y == rope.bottomY(). Bot grabbed
        // mid-rope, climbed toward the anchor, every fixed-step climb landed past bottomY,
        // beginFall(0,0) detached, and the loop repeated. The precise-target snap was the right
        // mechanism — it just refused to fire at bottomY because of an over-strict bounds check.
        Rope rope = new Rope(2352, 662, 863, false);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentClimbStateRuntime.setClimbingOnRope(entry, rope);
        AgentNavigationDebugStateRuntime.setNavPreciseTarget(entry, true);

        // Bot within one climbStep of the anchor — natural step would overshoot bottomY.
        int dyWithin = AgentMovementKinematicsService.climbStepPerTick() - 2;
        assertTrue(AgentClimbMovementService.shouldSnapToClimbTarget(
                entry, new Point(2352, rope.bottomY()), dyWithin));
        // dy >= climbStep: the natural step lands at-or-inside the (point) window. Old
        // behavior — no snap, let the integrator advance.
        int dyAtOrPastStep = AgentMovementKinematicsService.climbStepPerTick();
        assertFalse(AgentClimbMovementService.shouldSnapToClimbTarget(
                entry, new Point(2352, rope.bottomY()), dyAtOrPastStep));
    }

    @Test
    void shouldKeepClimbingUntilPhysicsDismountsTopStepOffExit() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(3398, 126));
        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getMap()).thenReturn(map);
        when(bot.getId()).thenReturn(1);
        when(bot.getHp()).thenReturn(100);
        when(map.getPointBelow(any(Point.class))).thenAnswer(invocation -> {
            Point probe = invocation.getArgument(0);
            return new Point(probe.x, 124);
        });

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentClimbStateRuntime.setClimbingOnRope(entry, new Rope(3398, 126, 332, false));
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, new AgentNavigationGraph.Edge(
                53, 25, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(3398, 156), new Point(3443, 124),
                0, 0, 3398, 126, 332, 400
        ));
        AgentNavigationDebugStateRuntime.setNavPreciseTarget(entry, true);

        AgentClimbMovementService.tickClimbing(entry, new Point(3398, 124), true);

        assertEquals(new Point(3398, 124), bot.getPosition());
        assertFalse(AgentClimbStateRuntime.climbing(entry));
        assertFalse(AgentMovementStateRuntime.inAir(entry));
    }

    @Test
    void shouldUseEdgeSpecificPreciseStopDist() {
        // Regression: pathlog-CRASH-2026-04-02 — bot 2px from CLIMB entry (969 vs 967),
        // stopDist=4 caused it to idle short of the entry, blocking canExecuteClimbEntry forever.
        // CLIMB still needs stopDist=1 to reach the exact anchor, but JUMP uses a launch window
        // and must keep walking until it is inside that window, so stopDist=0 is intentional.
        AgentNavigationGraph.Edge climbEdge = new AgentNavigationGraph.Edge(
                3, 27, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(967, 1545), new Point(879, 1545),
                0, 0, 879, 1503, 1545, 787
        );
        AgentNavigationGraph.Edge jumpEdge = new AgentNavigationGraph.Edge(
                1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(100, 0), new Point(200, -50),
                8, 0, 0, 0, 0, 300
        );
        AgentNavigationGraph.Edge downJumpEdge = new AgentNavigationGraph.Edge(
                1, 2, AgentNavigationGraph.EdgeType.DROP,
                new Point(100, 0), new Point(100, 120),
                96, 104, 0, 0, 0, 0, 0, 250
        );
        AgentNavigationGraph.Edge walkEdge = new AgentNavigationGraph.Edge(
                358, 355, AgentNavigationGraph.EdgeType.WALK,
                new Point(46, -61), new Point(54, -58),
                0, 0, 0, 0, 0, 100
        );

        assertEquals(1, AgentGroundMovementPolicy.preciseNavStopDist(climbEdge),
                "CLIMB entry must use stopDist=1 to reach exact anchor");
        assertEquals(0, AgentGroundMovementPolicy.preciseNavStopDist(jumpEdge),
                "JUMP entry must use stopDist=0 so the bot walks into the launch window");
        assertEquals(0, AgentGroundMovementPolicy.preciseNavStopDist(downJumpEdge),
                "straight down-jump DROP entry must use stopDist=0 so the bot walks into the launch window");
        assertEquals(4, AgentGroundMovementPolicy.preciseNavStopDist(walkEdge),
                "WALK traversal keeps stopDist=4 to absorb terrain micro-bumps");
        assertEquals(4, AgentGroundMovementPolicy.preciseNavStopDist(null),
                "null edge falls back to WALK tolerance");
    }

    @Test
    void shouldClearCommittedWalkEdgeWhenNextGroundStepIsNotWalkable() {
        MapleMap map = new MapleMap(910000011, 0, 0, 910000011, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(10, 100), 1));
        map.setFootholds(footholds);

        Character bot = mock(Character.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(8, 100));
        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getMap()).thenReturn(map);
        when(bot.getId()).thenReturn(1);
        when(bot.getHp()).thenReturn(100);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, new AgentNavigationGraph.Edge(
                1, 2, AgentNavigationGraph.EdgeType.WALK,
                new Point(8, 100), new Point(60, 100),
                0, 0, 0, 0, 0, 100
        ));
        AgentNavigationDebugStateRuntime.setNavPreciseTarget(entry, true);

        AgentGroundMovementRuntimeService.tickGrounded(entry, new Point(60, 100));

        assertNull(AgentNavigationDebugStateRuntime.activeNavigationEdge(entry));
        assertEquals(new Point(8, 100), bot.getPosition());
    }

    @Test
    void shouldJumpForwardWhenMobBlocksWalkLaneAndLandingStaysInCurrentRegion() {
        MapleMap map = spy(new MapleMap(910009048, 0, 0, 910009048, 1.0f));
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(300, 100), 1));
        map.setFootholds(footholds);
        AgentNavigationGraphService.rebuildGraph(map);
        doReturn(List.of(mockMob(new Point(130, 100), 100100))).when(map).getAllMonsters();

        Character bot = mockBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setFollowing(entry, true);

        AgentGroundMovementRuntimeService.tickGrounded(entry, new Point(250, 100));

        assertTrue(AgentMovementStateRuntime.inAir(entry), "grounded follow movement should jump over a mob blocking the walk lane");
        assertEquals(AgentMovementKinematicsService.walkStep(map, AgentMovementStateRuntime.movementProfile(entry)), AgentMovementPhysicsStateRuntime.airVelocityX(entry));

        AgentAirborneMovementService.tickAirborne(entry, new Point(250, 100));

        assertEquals(0.0, AgentMovementPhysicsStateRuntime.airSteerVelocityX(entry), 0.0001,
                "mob-avoid jumps should keep the simulated fixed forward arc");
    }

    @Test
    void shouldNotJumpOverBlockingMobWhenSimulatedLandingLeavesCurrentRegion() {
        MapleMap map = spy(new MapleMap(910009049, 0, 0, 910009049, 1.0f));
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(140, 100), 1));
        map.setFootholds(footholds);
        AgentNavigationGraphService.rebuildGraph(map);
        doReturn(List.of(mockMob(new Point(120, 100), 100100))).when(map).getAllMonsters();

        Character bot = mockBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setFollowing(entry, true);

        AgentGroundMovementRuntimeService.tickGrounded(entry, new Point(190, 100));

        assertFalse(AgentMovementStateRuntime.inAir(entry), "mob-avoid jump should be skipped when simulation would leave the current platform region");
    }

    @Test
    void shouldKeepClimbingTowardCommittedExitAnchorWhenStillAboveIt() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(-157, -28));
        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getMap()).thenReturn(map);
        when(bot.getId()).thenReturn(1);
        when(bot.getHp()).thenReturn(100);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentClimbStateRuntime.setClimbingOnRope(entry, new Rope(-157, -115, 118, false));
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, new AgentNavigationGraph.Edge(
                47, 39, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(-157, -25), new Point(-61, 121),
                8, 0, -157, -115, 118, 650
        ));

        AgentClimbMovementService.tickClimbing(entry, new Point(-157, -25), false);

        assertEquals(new Point(-157, -23), bot.getPosition());
    }

    @Test
    void shouldLandOnIntermediateBumpDuringAirborneTick() {
        MapleMap map = new MapleMap(910000005, 0, 0, 910000005, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 110), new Point(20, 110), 1));
        footholds.insert(new Foothold(new Point(4, 102), new Point(6, 102), 2));
        map.setFootholds(footholds);

        Character bot = mock(Character.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(0, 100));
        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getMap()).thenReturn(map);
        when(bot.getId()).thenReturn(1);
        when(bot.getHp()).thenReturn(100);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setInAir(entry, true);
        AgentMovementPhysicsStateRuntime.setPhysicsX(entry, 0);
        AgentMovementPhysicsStateRuntime.setPhysicsY(entry, 100);
        AgentMovementPhysicsStateRuntime.setVerticalVelocity(entry, 0f);
        AgentMovementPhysicsStateRuntime.setAirVelocityX(entry, 8);

        AgentAirborneMovementService.tickAirborne(entry, null);

        assertEquals(new Point(4, 102), bot.getPosition());
        assertFalse(AgentMovementStateRuntime.inAir(entry));
    }

    @Test
    void shouldKeepHorizontalMomentumWhenDroppingPastWallEndpoint() {
        MapleMap map = new MapleMap(910000006, 0, 0, 910000006, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 0), new Point(20, 0), 1));
        footholds.insert(new Foothold(new Point(0, 0), new Point(0, 80), 2));
        map.setFootholds(footholds);

        Character bot = mock(Character.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(0, 0));
        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getMap()).thenReturn(map);
        when(bot.getId()).thenReturn(1);
        when(bot.getHp()).thenReturn(100);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setInAir(entry, true);
        AgentMovementPhysicsStateRuntime.setPhysicsX(entry, 0);
        AgentMovementPhysicsStateRuntime.setPhysicsY(entry, 0);
        AgentMovementPhysicsStateRuntime.setVerticalVelocity(entry, 0f);
        AgentMovementPhysicsStateRuntime.setAirVelocityX(entry, -8);

        AgentAirborneMovementService.tickAirborne(entry, null);

        assertTrue(bot.getPosition().x < 0);
        assertEquals(-8, AgentMovementPhysicsStateRuntime.airVelocityX(entry));
    }

    @Test
    void shouldJumpAcrossSmallGapDuringGraphWarmupFallback() {
        MapleMap map = new MapleMap(910000031, 0, 0, 910000031, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(40, 100), 1));
        footholds.insert(new Foothold(new Point(80, 100), new Point(140, 100), 2));
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(36, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentNavigationDebugStateRuntime.setGraphWarmupFallback(entry, true);

        AgentGroundMovementRuntimeService.tickGrounded(entry, new Point(110, 100));

        assertTrue(AgentMovementStateRuntime.inAir(entry), "graph warmup fallback should jump small same-level gaps instead of freezing");
        assertEquals(AgentMovementKinematicsService.walkStep(map, AgentMovementStateRuntime.movementProfile(entry)), AgentMovementPhysicsStateRuntime.airVelocityX(entry));
    }

    @Test
    void shouldJumpOntoReachablePlatformWhenFallbackWalksIntoWall() {
        MapleMap map = new MapleMap(910000050, 0, 0, 910000050, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        Foothold lower = new Foothold(new Point(0, 100), new Point(50, 100), 1);
        Foothold wall = new Foothold(new Point(50, 60), new Point(50, 100), 2);
        Foothold upper = new Foothold(new Point(50, 60), new Point(120, 60), 3);
        wall.setNext(lower.getId());
        wall.setPrev(upper.getId());
        footholds.insert(lower);
        footholds.insert(wall);
        footholds.insert(upper);
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(44, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentNavigationDebugStateRuntime.setGraphWarmupFallback(entry, true);

        AgentGroundMovementRuntimeService.tickGrounded(entry, new Point(90, 60));

        assertTrue(AgentMovementStateRuntime.inAir(entry), "fallback should jump when a wall blocks walking but a platform is reachable");
        assertEquals(AgentMovementKinematicsService.walkStep(map, AgentMovementStateRuntime.movementProfile(entry)), AgentMovementPhysicsStateRuntime.airVelocityX(entry));
    }

    @Test
    void shouldAttachNearbyRopeDuringGraphWarmupFallbackWhenTargetIsAbove() {
        MapleMap map = new MapleMap(910000032, 0, 0, 910000032, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 120), new Point(200, 120), 1));
        map.setFootholds(footholds);
        map.addRope(new Rope(100, 40, 120, false));

        Character bot = mockBot(new Point(100, 120), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentNavigationDebugStateRuntime.setGraphWarmupFallback(entry, true);

        AgentGroundMovementRuntimeService.tickGrounded(entry, new Point(100, 40));

        assertTrue(AgentClimbStateRuntime.climbing(entry), "graph warmup fallback should use a nearby rope for vertical travel");
        assertEquals(new Point(100, 120), bot.getPosition());
    }

    @Test
    void shouldUseStopFollowHysteresisInsteadOfPacingSameRegionFollowMovement() {
        MapleMap map = new MapleMap(910000033, 0, 0, 910000033, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(200, 100), 1));
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(0, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentOwnerMotionStateRuntime.updateObservedOwnerStep(entry, new Point(0, 0));
        AgentOwnerMotionStateRuntime.rememberOwnerPosition(entry, new Point(0, 0));
        AgentOwnerMotionStateRuntime.updateObservedOwnerStep(entry, new Point(4, 0));

        int stoppedStep = AgentGroundMovementService.resolveGroundStepX(
                entry, new Point(0, 100), new Point(20, 100), AgentMovementPhysicsConfig.configuredStopDist(), AgentMovementPhysicsConfig.configuredFollowDist());
        assertEquals(0, stoppedStep,
                "follow should stop anywhere inside STOP_DIST instead of micro-throttling to an exact point");

        int walkStep = AgentMovementKinematicsService.walkStep(map, AgentMovementStateRuntime.movementProfile(entry));
        int followStep = AgentGroundMovementService.resolveGroundStepX(
                entry, new Point(0, 100), new Point(90, 100), AgentMovementPhysicsConfig.configuredStopDist(), AgentMovementPhysicsConfig.configuredFollowDist());

        assertEquals(walkStep, followStep,
                "follow should restart at FOLLOW_DIST using normal full-speed movement");
    }

    @Test
    void shouldUseFullWalkStepForFastFollowBotsInsteadOfPacing() {
        MapleMap map = new MapleMap(910000034, 0, 0, 910000034, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(300, 100), 1));
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(0, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentMovementStateRuntime.setWasMovingX(entry, true);
        AgentMovementStateRuntime.setMovementProfile(entry, new AgentMovementProfile(140, 100));
        AgentOwnerMotionStateRuntime.rememberOwnerPosition(entry, new Point(0, 0));
        AgentOwnerMotionStateRuntime.updateObservedOwnerStep(entry, new Point(4, 0));

        int walkStep = AgentMovementKinematicsService.walkStep(map, AgentMovementStateRuntime.movementProfile(entry));
        int step = AgentGroundMovementService.resolveGroundStepX(
                entry, new Point(0, 100), new Point(60, 100), AgentMovementPhysicsConfig.configuredStopDist(), AgentMovementPhysicsConfig.configuredFollowDist());

        assertEquals(walkStep, step,
                "fast follow bots should keep full walk speed instead of being micro-throttled");
    }

    @Test
    void shouldShowStandingStanceWhileGroundVelocityDeceleratesWithoutMoveInput() {
        MapleMap map = new MapleMap(910000035, 0, 0, 910000035, 1.0f);
        Character bot = mockBot(new Point(0, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setInAir(entry, false);
        AgentMovementStateRuntime.setMovementVelocity(entry, 80, AgentMovementStateRuntime.movementVelocityY(entry));
        AgentMovementStateRuntime.setMoveDirection(entry, 0);
        AgentMovementStateRuntime.setFacingDirection(entry, 1);

        assertTrue(AgentMovementPoseService.isStandingResolvedStance(entry),
                "residual ground velocity should not force a walking stance when no move key is held");
    }

    @Test
    void shouldNotUseSpeedMismatchFidgetWhenOwnerIsIdle() {
        MapleMap map = new MapleMap(910000041, 0, 0, 910000041, 1.0f);
        Character bot = mockBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentMovementStateRuntime.setMovementProfile(entry, new AgentMovementProfile(140, 100));
        AgentOwnerMotionStateRuntime.clearObservedOwnerStep(entry);

        assertFalse(AgentFidgetService.shouldStartSpeedMismatchFidget(entry, new Point(100, 100), new Point(110, 100)),
                "idle owners should use the long idle-fidget roll, not the active follow speed-mismatch fidget");

        AgentOwnerMotionStateRuntime.rememberOwnerPosition(entry, new Point(0, 0));
        AgentOwnerMotionStateRuntime.updateObservedOwnerStep(entry, new Point(4, 0));

        assertTrue(AgentFidgetService.shouldStartSpeedMismatchFidget(entry, new Point(100, 100), new Point(110, 100)),
                "slow-but-moving owners remain eligible for speed-mismatch follow fidgets");
    }

    @Test
    void shouldHoldProneWhileFidgetIsActive() {
        MapleMap map = new MapleMap(910000036, 0, 0, 910000036, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(300, 100), 1));
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentMovementStateRuntime.setMovementProfile(entry, new AgentMovementProfile(140, 100));
        AgentFidgetService.startFidget(entry, AgentFidgetMode.PRONE, System.currentTimeMillis(), 3000);

        assertTrue(AgentFidgetService.tryHandleTick(entry, new Point(110, 100), true));
        assertTrue(AgentMovementStateRuntime.crouching(entry));
    }

    @Test
    void shouldNotChangeDirectionForProneFidgets() {
        MapleMap map = new MapleMap(910000048, 0, 0, 910000048, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(300, 100), 1));
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentMovementStateRuntime.setFacingDirection(entry, -1);
        AgentFidgetService.startFidget(entry, AgentFidgetMode.PRONE, System.currentTimeMillis(), 3000);

        assertTrue(AgentFidgetService.tryHandleTick(entry, new Point(110, 100), true));
        assertEquals(-1, AgentMovementStateRuntime.facingDirection(entry), "prone fidget should keep the current facing direction");
        assertEquals(CharacterStance.PRONE_LEFT_STANCE, bot.getStance(),
                "prone fidget should send left-facing prone stance");

        AgentFidgetService.clear(entry);
        AgentMovementStateRuntime.setFacingDirection(entry, -1);
        AgentMovementStateRuntime.setCrouching(entry, false);
        AgentFidgetService.startFidget(entry, AgentFidgetMode.SPAM_PRONE, System.currentTimeMillis(), 3000);

        assertTrue(AgentFidgetService.tryHandleTick(entry, new Point(110, 100), true));
        assertEquals(-1, AgentMovementStateRuntime.facingDirection(entry), "spam-prone fidget should not synthesize a turn input");
        assertEquals(CharacterStance.PRONE_LEFT_STANCE, bot.getStance(),
                "spam-prone fidget should send left-facing prone stance");
    }

    @Test
    void shouldAllowSocialFidgetsAtBaseMoveSpeed() {
        MapleMap map = new MapleMap(910000038, 0, 0, 910000038, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(300, 100), 1));
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentMovementStateRuntime.setMovementProfile(entry, new AgentMovementProfile(100, 100));
        AgentFidgetService.startFidget(entry, AgentFidgetMode.PRONE, System.currentTimeMillis(), 3000, AgentFidgetTrigger.SOCIAL);

        assertTrue(AgentFidgetService.tryHandleTick(entry, new Point(110, 100), true));
        assertTrue(AgentMovementStateRuntime.crouching(entry));
    }

    @Test
    void shouldAllowAutoFollowFidgetsAtBaseMoveSpeed() {
        MapleMap map = new MapleMap(910000047, 0, 0, 910000047, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(300, 100), 1));
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentMovementStateRuntime.setMovementProfile(entry, new AgentMovementProfile(100, 100));
        AgentFidgetService.startFidget(entry, AgentFidgetMode.PRONE, System.currentTimeMillis(), 3000, AgentFidgetTrigger.AUTO_FOLLOW);

        assertTrue(AgentFidgetService.tryHandleTick(entry, new Point(110, 100), true));
        assertTrue(AgentMovementStateRuntime.crouching(entry), "base-speed follow fidgets should not be blocked by a speed-stat guard");
    }

    @Test
    void shouldAlternateDirectionalFidgetJumps() {
        MapleMap map = new MapleMap(910000039, 0, 0, 910000039, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(300, 100), 1));
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentMovementStateRuntime.setMovementProfile(entry, new AgentMovementProfile(140, 100));
        AgentFidgetService.startFidget(entry, AgentFidgetMode.DIAGONAL_JUMP, System.currentTimeMillis(), 3000);

        assertTrue(AgentFidgetService.tryHandleTick(entry, new Point(110, 100), true));
        int firstJumpVelX = AgentMovementPhysicsStateRuntime.airVelocityX(entry);
        assertTrue(firstJumpVelX != 0, "diagonal jump fidget should launch with horizontal momentum");

        AgentMovementPoseService.idleOnGround(entry, bot);
        entry.fidgetState().setNextJumpAtMs(0L);

        assertTrue(AgentFidgetService.tryHandleTick(entry, new Point(110, 100), true));
        assertEquals(-Integer.signum(firstJumpVelX), Integer.signum(AgentMovementPhysicsStateRuntime.airVelocityX(entry)),
                "diagonal jump fidget should alternate jump direction on the next grounded launch");
    }

    @Test
    void shouldKeepJumpFidgetRunningWhileAirborne() {
        MapleMap map = new MapleMap(910000040, 0, 0, 910000040, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(300, 100), 1));
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentMovementStateRuntime.setMovementProfile(entry, new AgentMovementProfile(140, 100));
        AgentFidgetService.startFidget(entry, AgentFidgetMode.JUMP, System.currentTimeMillis(), 3000);

        assertTrue(AgentFidgetService.tryHandleTick(entry, new Point(110, 100), true));
        assertTrue(AgentMovementStateRuntime.inAir(entry));

        bot.setPosition(new Point(100, 0));
        assertTrue(AgentFidgetService.tryHandleTick(entry, new Point(110, 100), true));
        assertEquals(AgentFidgetMode.JUMP, AgentFidgetStateRuntime.mode(entry),
                "jump fidgets should not clear themselves while airborne above the ground target");
    }

    @Test
    void shouldRepeatJumpFidgetAfterLandingUntilDurationEnds() {
        MapleMap map = new MapleMap(910000042, 0, 0, 910000042, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(300, 100), 1));
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentMovementStateRuntime.setMovementProfile(entry, new AgentMovementProfile(140, 100));
        AgentFidgetService.startFidget(entry, AgentFidgetMode.JUMP, System.currentTimeMillis(), 3000);

        assertTrue(AgentFidgetService.tryHandleTick(entry, new Point(110, 100), true));
        assertTrue(AgentMovementStateRuntime.inAir(entry));

        AgentMovementPoseService.idleOnGround(entry, bot);
        entry.fidgetState().setNextActionAtMs(Long.MAX_VALUE);
        entry.fidgetState().setNextJumpAtMs(0L);

        assertTrue(AgentFidgetService.tryHandleTick(entry, new Point(110, 100), true));
        assertTrue(AgentMovementStateRuntime.inAir(entry), "grounded jump fidgets should launch again even if air steering is cooling down");
    }

    @Test
    void shouldOnlySpamAirSteerWhenJumpFidgetRollEnablesIt() {
        MapleMap map = new MapleMap(910000046, 0, 0, 910000046, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(300, 100), 1));
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentMovementStateRuntime.setMovementProfile(entry, new AgentMovementProfile(140, 100));
        AgentFidgetService.startFidget(entry, AgentFidgetMode.JUMP, System.currentTimeMillis(), 3000);
        AgentFidgetService.tryHandleTick(entry, new Point(110, 100), true);

        entry.fidgetState().setSpamAirSteer(false);
        AgentMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
        entry.fidgetState().setNextActionAtMs(0L);

        assertTrue(AgentFidgetService.tryHandleTick(entry, new Point(110, 100), true));
        assertEquals(0.0, AgentMovementPhysicsStateRuntime.airSteerVelocityX(entry),
                "non-spam jump fidgets should not reroll random air steering every airborne tick");

        entry.fidgetState().setSpamAirSteer(true);
        entry.fidgetState().setActionBaseDelayMs(100);
        AgentMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
        entry.fidgetState().setNextActionAtMs(0L);
        long before = System.currentTimeMillis();

        assertTrue(AgentFidgetService.tryHandleTick(entry, new Point(110, 100), true));
        assertTrue(AgentMovementPhysicsStateRuntime.airSteerVelocityX(entry) != 0.0,
                "spam-air-steer jump fidgets should press random side input on their own delay");
        long after = System.currentTimeMillis();
        assertTrue(AgentFidgetStateRuntime.nextActionAtMs(entry) >= before + 100
                        && AgentFidgetStateRuntime.nextActionAtMs(entry) <= after + 150,
                "air-steer spam should use a tick-aligned 0/50ms jitter");
    }

    @Test
    void shouldSpamSidewaysDuringFidgetWithoutDroppingFollowMode() {
        MapleMap map = new MapleMap(910000043, 0, 0, 910000043, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(300, 100), 1));
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentMovementStateRuntime.setMovementProfile(entry, new AgentMovementProfile(140, 100));
        AgentFidgetService.startFidget(entry, AgentFidgetMode.SPAM_SIDEWAYS, System.currentTimeMillis(), 3000);
        assertTrue(AgentFidgetStateRuntime.actionBaseDelayMs(entry) >= 100 && AgentFidgetStateRuntime.actionBaseDelayMs(entry) <= 250);
        assertEquals(0, AgentFidgetStateRuntime.actionBaseDelayMs(entry) % AgentMovementPhysicsConfig.configuredMovementTickMs());

        long before = System.currentTimeMillis();
        assertTrue(AgentFidgetService.tryHandleTick(entry, new Point(110, 100), true));
        assertEquals(AgentFidgetMode.SPAM_SIDEWAYS, AgentFidgetStateRuntime.mode(entry));
        assertTrue(AgentFidgetStateRuntime.moveDir(entry) != 0, "sideway spam should keep an active sideways fidget direction");
        assertTrue(bot.getPosition().x != 100, "sideway spam should cause sideways motion during the tick");
        long after = System.currentTimeMillis();
        assertTrue(AgentFidgetStateRuntime.nextActionAtMs(entry) >= before + AgentFidgetStateRuntime.actionBaseDelayMs(entry)
                        && AgentFidgetStateRuntime.nextActionAtMs(entry) <= after + AgentFidgetStateRuntime.actionBaseDelayMs(entry) + 50,
                "sideway spam should use tick-aligned 0/50ms jitter around its per-fidget base interval");
        assertTrue(AgentModeStateRuntime.following(entry), "sideway spam should not convert follow mode into a manual move command");
    }

    @Test
    void shouldContinueFollowingAfterAutoFidgetEnds() {
        MapleMap map = new MapleMap(910000044, 0, 0, 910000044, 1.0f);
        Character bot = mockBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentMovementStateRuntime.setMovementProfile(entry, new AgentMovementProfile(140, 100));
        long now = System.currentTimeMillis();
        AgentFidgetService.startFidget(entry, AgentFidgetMode.SPAM_SIDEWAYS, now, 2000);
        bot.setPosition(new Point(130, 100));
        entry.fidgetState().setUntilMs(now - 1);

        assertFalse(AgentFidgetService.tryHandleTick(entry, new Point(110, 100), true));
        assertEquals(AgentFidgetMode.NONE, AgentFidgetStateRuntime.mode(entry));
        assertNull(AgentMoveTargetStateRuntime.moveTarget(entry),
                "speed-mismatch follow fidgets should resume following immediately");
        assertFalse(AgentMoveTargetStateRuntime.isPrecise(entry));
    }

    @Test
    void shouldReturnToFidgetOriginWithPreciseMoveTargetAfterIdleOrSocialFidgetEnds() {
        MapleMap map = new MapleMap(910000045, 0, 0, 910000045, 1.0f);
        Character bot = mockBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentMovementStateRuntime.setMovementProfile(entry, new AgentMovementProfile(140, 100));
        long now = System.currentTimeMillis();
        AgentFidgetService.startFidget(entry, AgentFidgetMode.SPAM_SIDEWAYS, now, 2000, AgentFidgetTrigger.SOCIAL);
        bot.setPosition(new Point(130, 100));
        entry.fidgetState().setUntilMs(now - 1);

        assertFalse(AgentFidgetService.tryHandleTick(entry, new Point(110, 100), true));
        assertEquals(new Point(100, 100), AgentMoveTargetStateRuntime.moveTarget(entry),
                "social fidget cleanup should reuse the precise move-target path from the here command");
        assertTrue(AgentMoveTargetStateRuntime.isPrecise(entry));

        AgentMoveTargetStateRuntime.clearMoveTarget(entry);
        bot.setPosition(new Point(130, 100));
        AgentFidgetService.startFidget(entry, AgentFidgetMode.SPAM_SIDEWAYS, now, 2000, AgentFidgetTrigger.IDLE);
        bot.setPosition(new Point(160, 100));
        entry.fidgetState().setUntilMs(now - 1);

        assertFalse(AgentFidgetService.tryHandleTick(entry, new Point(110, 100), true));
        assertEquals(new Point(130, 100), AgentMoveTargetStateRuntime.moveTarget(entry),
                "idle fidget cleanup should return to its own recorded origin");
        assertTrue(AgentMoveTargetStateRuntime.isPrecise(entry));
    }

    @Test
    void shouldNotUseDownJumpForUnstuckRecovery() {
        MapleMap map = new MapleMap(910000037, 0, 0, 910000037, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(300, 100), 1));
        map.setFootholds(footholds);

        Character bot = mockBot(new Point(100, 100), map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        AgentMovementRecoveryService.tickUnstuck(entry);

        assertFalse(AgentMovementStateRuntime.downJumpPending(entry), "unstuck recovery should only use lateral jumps");
        assertTrue(AgentMovementStateRuntime.inAir(entry), "unstuck recovery should launch the bot instead of crouching in place");
    }

    @Test
    void shouldNotApplyAirSteeringDuringCommittedNavJump() {
        MapleMap map = new MapleMap(910000009, 0, 0, 910000009, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(-200, 200), new Point(200, 200), 1));
        map.setFootholds(footholds);

        Character bot = mock(Character.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(100, 100));
        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getMap()).thenReturn(map);
        when(bot.getId()).thenReturn(1);
        when(bot.getHp()).thenReturn(100);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setInAir(entry, true);
        AgentMovementPhysicsStateRuntime.setPhysicsX(entry, 100);
        AgentMovementPhysicsStateRuntime.setPhysicsY(entry, 100);
        AgentMovementPhysicsStateRuntime.setVerticalVelocity(entry, 0f);
        AgentMovementPhysicsStateRuntime.setAirVelocityX(entry, -8);
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, new AgentNavigationGraph.Edge(
                1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(100, 100), new Point(50, 50),
                -8, 0, 0, 0, 0, 300
        ));

        AgentAirborneMovementService.tickAirborne(entry, new Point(-300, 100));

        assertEquals(0.0, AgentMovementPhysicsStateRuntime.airSteerVelocityX(entry), 0.0001);
        assertEquals(new Point(92, 103), bot.getPosition());
    }

    @Test
    void shouldKeepRopeGrabEnabledWhenJumpingFromRopeToRope() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(bot.getMap()).thenReturn(map);
        when(bot.getPosition()).thenReturn(new Point(668, 1757));
        when(bot.getHp()).thenReturn(100);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentClimbStateRuntime.setClimbingOnRope(entry, new Rope(668, 1727, 1980, false));

        AgentClimbMovementService.jumpToRope(entry, bot, 8);

        assertTrue(AgentMovementStateRuntime.inAir(entry));
        assertTrue(AgentClimbStateRuntime.climbUpIntent(entry));
        assertEquals(0, AgentClimbStateRuntime.ropeGrabCooldownMs(entry));
        assertEquals(668, AgentClimbStateRuntime.blockedRopeGrab(entry).x());
    }

    @Test
    void shouldSwapMovementProfileWhileBucketGraphWarms() {
        MapleMap map = new MapleMap(910000027, 0, 0, 910000027, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(200, 100), 1));
        map.setFootholds(footholds);
        AgentNavigationGraphService.rebuildGraph(map, AgentMovementProfile.base());

        Character bot = mockBot(new Point(20, 100), map);
        when(bot.getTotalMoveSpeedStat()).thenReturn(109);
        when(bot.getTotalJumpStat()).thenReturn(107);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setMovementProfile(entry, AgentMovementProfile.base());

        AgentMovementProfile targetProfile = AgentMovementProfile.fromCharacter(bot);
        assertEquals(new AgentMovementProfile(105, 105), targetProfile);
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, new AgentNavigationGraph.Edge(
                1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(20, 100), new Point(80, 40),
                8, 0, 0, 0, 0, 300
        ));
        AgentNavigationDebugStateRuntime.setNavTargetPosition(entry, new Point(20, 100));
        AgentNavigationDebugStateRuntime.setNavTargetRegionId(entry, 2);
        AgentNavigationDebugStateRuntime.setNavPreciseTarget(entry, true);

        assertTrue(AgentMovementProfileService.refreshMovementProfile(entry),
                "profile swap should commit immediately and let nav use closest graph while the exact graph warms");
        assertEquals(targetProfile, AgentMovementStateRuntime.movementProfile(entry));
        assertNull(AgentNavigationDebugStateRuntime.activeNavigationEdge(entry));
        assertNull(AgentNavigationDebugStateRuntime.navTargetPosition(entry));
        assertEquals(-1, AgentNavigationDebugStateRuntime.navTargetRegionId(entry));
        assertFalse(AgentNavigationDebugStateRuntime.navPreciseTarget(entry));
    }

    private static Character mockBot(Point startPosition, MapleMap map) {
        Character bot = mock(Character.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(startPosition));
        AtomicInteger stance = new AtomicInteger();
        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getStance()).thenAnswer(invocation -> stance.get());
        doAnswer(invocation -> {
            stance.set(invocation.getArgument(0));
            return null;
        }).when(bot).setStance(anyInt());
        when(bot.getMap()).thenReturn(map);
        when(bot.getId()).thenReturn(1);
        when(bot.getHp()).thenReturn(100);
        when(bot.getTotalMoveSpeedStat()).thenReturn(100);
        when(bot.getTotalJumpStat()).thenReturn(100);
        return bot;
    }

    private static Monster mockMob(Point position, int id) {
        Monster mob = mock(Monster.class);
        when(mob.getPosition()).thenReturn(new Point(position));
        when(mob.getId()).thenReturn(id);
        when(mob.isAlive()).thenReturn(true);
        when(mob.isFacingLeft()).thenReturn(false);
        return mob;
    }
}
