package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotMovementManagerTest {
    @Test
    void shouldClampGrindingTargetAwayFromCurrentFootholdEdgeForSameFootholdCombat() {
        MapleMap map = new MapleMap(910000007, 0, 0, 910000007, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        Foothold foothold = new Foothold(new Point(0, 100), new Point(200, 100), 1);
        footholds.insert(foothold);
        map.setFootholds(footholds);
        BotNavigationGraphProvider.rebuildGraph(map);

        Character bot = mockBot(new Point(100, 100), map);

        BotEntry entry = new BotEntry(bot, null, null);
        entry.grinding = true;

        Point adjusted = BotMovementManager.adjustGrindingTargetPosition(entry, foothold, new Point(190, 100));

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
        BotNavigationGraphProvider.rebuildGraph(map);

        Character bot = mockBot(new Point(-100, 100), map);

        BotEntry entry = new BotEntry(bot, null, null);
        entry.grinding = true;

        Point targetPos = new Point(190, 100);
        Point adjusted = BotMovementManager.adjustGrindingTargetPosition(entry, leftFoothold, targetPos);

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
        BotNavigationGraphProvider.rebuildGraph(map);

        Character bot = mockBot(new Point(-150, 100), map);
        BotEntry entry = new BotEntry(bot, null, null);
        entry.grinding = true;

        Point adjusted = BotMovementManager.adjustGrindingTargetPosition(entry, leftFoothold, new Point(190, 100));

        assertEquals(new Point(160, 100), adjusted);
    }

    @Test
    void shouldNotClampGrindingTargetForSmallRegion() {
        MapleMap map = new MapleMap(910000024, 0, 0, 910000024, 1.0f);
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        Foothold foothold = new Foothold(new Point(0, 100), new Point(60, 100), 1);
        footholds.insert(foothold);
        map.setFootholds(footholds);
        BotNavigationGraphProvider.rebuildGraph(map);

        Character bot = mockBot(new Point(20, 100), map);
        BotEntry entry = new BotEntry(bot, null, null);
        entry.grinding = true;

        Point targetPos = new Point(55, 100);
        Point adjusted = BotMovementManager.adjustGrindingTargetPosition(entry, foothold, targetPos);

        assertEquals(targetPos, adjusted);
    }

    @Test
    void shouldNotAirSteerCommittedRopeExitClimbArc() {
        MapleMap map = new MapleMap(910000025, 0, 0, 910000025, 1.0f);
        map.setFootholds(new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000)));
        Character bot = mockBot(new Point(0, 0), map);
        BotEntry entry = new BotEntry(bot, null, null);
        entry.inAir = true;
        entry.airVelX = -8;
        entry.physX = 0;
        entry.physY = 0;
        entry.velY = -10f;
        entry.navEdge = new BotNavigationGraph.Edge(
                25, 14, BotNavigationGraph.EdgeType.CLIMB,
                new Point(-437, -181), new Point(-473, -211),
                -8, 0, -437, -1471, 84, 250
        );

        BotMovementManager.tickAirborne(entry, new Point(300, 0));

        assertEquals(0.0, entry.airSteerVelX);
    }

    @Test
    void shouldNotHoldClimbIdleWhileCommittedClimbEdgeIsActive() {
        BotEntry entry = new BotEntry(null, null, null);
        entry.navEdge = new BotNavigationGraph.Edge(
                1, 2, BotNavigationGraph.EdgeType.CLIMB,
                new Point(0, 0), new Point(0, -100),
                0, 0, 10, -100, 40, 100
        );

        assertFalse(BotMovementManager.shouldHoldClimbIdle(entry, 0, 0));
    }

    @Test
    void shouldAllowIdleClimbHoldWithoutCommittedClimbEdge() {
        BotEntry entry = new BotEntry(null, null, null);

        assertTrue(BotMovementManager.shouldHoldClimbIdle(entry, 0, 0));
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

        assertTrue(BotPhysicsEngine.canWalkGroundStep(map, new Point(-73, 151), 8));
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

        BotEntry entry = new BotEntry(bot, null, null);
        entry.climbing = true;
        entry.climbRope = new Rope(668, 1727, 1980, false);
        entry.navEdge = new BotNavigationGraph.Edge(
                68, 54, BotNavigationGraph.EdgeType.CLIMB,
                new Point(668, 1757), new Point(796, 2025),
                8, 0, 668, 1727, 1980, 650
        );

        BotMovementManager.tickClimbing(entry, new Point(668, 1757), false);

        assertEquals(new Point(668, 1757), bot.getPosition());
    }

    @Test
    void shouldUseEdgeSpecificPreciseStopDist() {
        // Regression: pathlog-CRASH-2026-04-02 — bot 2px from CLIMB entry (969 vs 967),
        // stopDist=4 caused it to idle short of the entry, blocking canExecuteClimbEntry forever.
        // CLIMB still needs stopDist=1 to reach the exact anchor, but JUMP uses a launch window
        // and must keep walking until it is inside that window, so stopDist=0 is intentional.
        BotNavigationGraph.Edge climbEdge = new BotNavigationGraph.Edge(
                3, 27, BotNavigationGraph.EdgeType.CLIMB,
                new Point(967, 1545), new Point(879, 1545),
                0, 0, 879, 1503, 1545, 787
        );
        BotNavigationGraph.Edge jumpEdge = new BotNavigationGraph.Edge(
                1, 2, BotNavigationGraph.EdgeType.JUMP,
                new Point(100, 0), new Point(200, -50),
                8, 0, 0, 0, 0, 300
        );
        BotNavigationGraph.Edge walkEdge = new BotNavigationGraph.Edge(
                358, 355, BotNavigationGraph.EdgeType.WALK,
                new Point(46, -61), new Point(54, -58),
                0, 0, 0, 0, 0, 100
        );

        assertEquals(1, BotMovementManager.preciseNavStopDist(climbEdge),
                "CLIMB entry must use stopDist=1 to reach exact anchor");
        assertEquals(0, BotMovementManager.preciseNavStopDist(jumpEdge),
                "JUMP entry must use stopDist=0 so the bot walks into the launch window");
        assertEquals(4, BotMovementManager.preciseNavStopDist(walkEdge),
                "WALK traversal keeps stopDist=4 to absorb terrain micro-bumps");
        assertEquals(4, BotMovementManager.preciseNavStopDist(null),
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

        BotEntry entry = new BotEntry(bot, null, null);
        entry.navEdge = new BotNavigationGraph.Edge(
                1, 2, BotNavigationGraph.EdgeType.WALK,
                new Point(8, 100), new Point(60, 100),
                0, 0, 0, 0, 0, 100
        );
        entry.navPreciseTarget = true;

        BotMovementManager.tickGrounded(entry, new Point(60, 100));

        assertNull(entry.navEdge);
        assertEquals(new Point(8, 100), bot.getPosition());
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

        BotEntry entry = new BotEntry(bot, null, null);
        entry.climbing = true;
        entry.climbRope = new Rope(-157, -115, 118, false);
        entry.navEdge = new BotNavigationGraph.Edge(
                47, 39, BotNavigationGraph.EdgeType.CLIMB,
                new Point(-157, -25), new Point(-61, 121),
                8, 0, -157, -115, 118, 650
        );

        BotMovementManager.tickClimbing(entry, new Point(-157, -25), false);

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

        BotEntry entry = new BotEntry(bot, null, null);
        entry.inAir = true;
        entry.physX = 0;
        entry.physY = 100;
        entry.velY = 0f;
        entry.airVelX = 8;

        BotMovementManager.tickAirborne(entry, null);

        assertEquals(new Point(4, 102), bot.getPosition());
        assertFalse(entry.inAir);
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

        BotEntry entry = new BotEntry(bot, null, null);
        entry.inAir = true;
        entry.physX = 0;
        entry.physY = 0;
        entry.velY = 0f;
        entry.airVelX = -8;

        BotMovementManager.tickAirborne(entry, null);

        assertTrue(bot.getPosition().x < 0);
        assertEquals(-8, entry.airVelX);
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

        BotEntry entry = new BotEntry(bot, null, null);
        entry.inAir = true;
        entry.physX = 100;
        entry.physY = 100;
        entry.velY = 0f;
        entry.airVelX = -8;
        entry.navEdge = new BotNavigationGraph.Edge(
                1, 2, BotNavigationGraph.EdgeType.JUMP,
                new Point(100, 100), new Point(50, 50),
                -8, 0, 0, 0, 0, 300
        );

        BotMovementManager.tickAirborne(entry, new Point(-300, 100));

        assertEquals(0.0, entry.airSteerVelX, 0.0001);
        assertEquals(new Point(92, 103), bot.getPosition());
    }

    @Test
    void shouldKeepRopeGrabEnabledWhenJumpingFromRopeToRope() {
        Character bot = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(bot.getMap()).thenReturn(map);
        when(bot.getPosition()).thenReturn(new Point(668, 1757));
        when(bot.getHp()).thenReturn(100);

        BotEntry entry = new BotEntry(bot, null, null);
        entry.climbing = true;
        entry.climbRope = new Rope(668, 1727, 1980, false);

        BotMovementManager.jumpToRope(entry, bot, 8);

        assertTrue(entry.inAir);
        assertTrue(entry.climbUpIntent);
        assertEquals(0, entry.ropeGrabCooldownMs);
        assertEquals(668, entry.blockedRopeGrab.x());
    }

    private static Character mockBot(Point startPosition, MapleMap map) {
        Character bot = mock(Character.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(startPosition));
        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getMap()).thenReturn(map);
        when(bot.getId()).thenReturn(1);
        when(bot.getHp()).thenReturn(100);
        return bot;
    }
}
