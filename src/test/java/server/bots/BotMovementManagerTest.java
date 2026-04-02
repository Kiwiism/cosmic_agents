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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotMovementManagerTest {
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

        assertTrue(BotMovementManager.isPathWalkable(bot, new Point(-73, 151), 8));
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
    void shouldUseTightStopDistForNonWalkPreciseNavEntryPoints() {
        // Regression: pathlog-CRASH-2026-04-02 — bot 2px from CLIMB entry (969 vs 967),
        // stopDist=4 caused it to idle short of the entry, blocking canExecuteClimbEntry forever.
        // Non-WALK precise edges require stopDist=1 so the bot reaches the exact entry position.
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
        assertEquals(1, BotMovementManager.preciseNavStopDist(jumpEdge),
                "JUMP entry must use stopDist=1 to reach exact anchor");
        assertEquals(4, BotMovementManager.preciseNavStopDist(walkEdge),
                "WALK traversal keeps stopDist=4 to absorb terrain micro-bumps");
        assertEquals(4, BotMovementManager.preciseNavStopDist(null),
                "null edge falls back to WALK tolerance");
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
}
