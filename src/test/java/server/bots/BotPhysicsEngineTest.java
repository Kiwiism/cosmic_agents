package server.bots;

import client.Character;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import server.maps.MapleMap;
import server.maps.Foothold;
import server.maps.Rope;

import java.awt.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotPhysicsEngineTest {
    private static MapleMap henesys;
    private static MapleMap ellinia;

    @BeforeAll
    static void loadMaps() {
        System.setProperty("wz-path", Path.of("wz").toAbsolutePath().toString());
        henesys = BotNavigationMapLoader.loadMapGeometry(100000000);
        ellinia = BotNavigationMapLoader.loadMapGeometry(101000000);
    }

    @Test
    void shouldSharePhysicsConfigBetweenMovementManagerAndEngine() {
        assertSame(BotMovementManager.cfg, BotPhysicsEngine.cfg);
    }

    @Test
    void shouldSimulateKnownHenesysVerticalJumpLanding() {
        BotPhysicsEngine.JumpLanding landing = BotPhysicsEngine.simulateJumpLanding(henesys, new Point(1080, 334), 0);

        assertNotNull(landing);
        assertEquals(new Point(1080, 275), landing.point());
    }

    @Test
    void shouldTreatNearbyElliniaRopeAsReachableAndFarOffsetAsUnreachable() {
        Rope rope = ellinia.getRopes().stream()
                .filter(candidate -> candidate.topY() < candidate.bottomY())
                .findFirst()
                .orElseThrow();

        Point nearPoint = new Point(rope.x() - BotPhysicsEngine.walkStep(ellinia), rope.bottomY());
        Point farPoint = new Point(rope.x() - BotPhysicsEngine.maxJumpHorizontalTravel(ellinia) - 50, rope.bottomY());

        assertTrue(BotPhysicsEngine.canReachRopeFromGround(ellinia, nearPoint, rope));
        assertFalse(BotPhysicsEngine.canReachRopeFromGround(ellinia, farPoint, rope));
    }

    @Test
    void shouldClearMovementStateOnReset() {
        BotEntry entry = new BotEntry(null, null, null);
        entry.inAir = true;
        entry.climbing = true;
        entry.crouching = true;
        entry.climbUpIntent = true;
        entry.velY = 7f;
        entry.airVelX = 12;
        entry.physX = 99;
        entry.physY = 88;
        entry.movementVelX = 123;
        entry.movementVelY = -456;
        entry.downJumpPending = true;
        entry.downJumpGracePeriodMS = 350;

        BotPhysicsEngine.resetMotion(entry, new Point(10, 20));

        assertFalse(entry.inAir);
        assertFalse(entry.climbing);
        assertFalse(entry.crouching);
        assertFalse(entry.climbUpIntent);
        assertFalse(entry.downJumpPending);
        assertEquals(0L, entry.downJumpGracePeriodMS);
        assertEquals(10.0, entry.physX);
        assertEquals(20.0, entry.physY);
        assertEquals(0, entry.movementVelX);
        assertEquals(0, entry.movementVelY);
        assertEquals(BotPhysicsEngine.cfg.STAND_RIGHT_STANCE, BotPhysicsEngine.resolveStance(entry));
    }

    @Test
    void shouldDeriveMovementSnapshotFromPhysicsState() {
        BotEntry entry = new BotEntry(null, null, null);
        entry.inAir = true;
        entry.facingDir = -1;
        entry.movementVelX = -180;
        entry.movementVelY = -240;

        BotPhysicsEngine.MovementSnapshot snapshot = BotPhysicsEngine.movementSnapshot(entry);

        assertEquals(-180, snapshot.velX());
        assertEquals(-240, snapshot.velY());
        assertEquals(BotPhysicsEngine.cfg.JUMP_LEFT_STANCE, snapshot.stance());
    }

    @Test
    void shouldResolveIdleGroundStanceFromLastFacingDirection() {
        BotEntry entry = new BotEntry(null, null, null);

        entry.facingDir = 1;
        assertEquals(BotPhysicsEngine.cfg.STAND_RIGHT_STANCE, BotPhysicsEngine.resolveStance(entry));

        entry.facingDir = -1;
        assertEquals(BotPhysicsEngine.cfg.STAND_LEFT_STANCE, BotPhysicsEngine.resolveStance(entry));
    }

    @Test
    void shouldResolveDeadStanceFromLastFacingDirection() {
        Character bot = mockBot(new Point(10, 20), null, 0);
        BotEntry entry = new BotEntry(bot, null, null);

        entry.facingDir = 1;
        assertEquals(BotPhysicsEngine.cfg.DEAD_RIGHT_STANCE, BotPhysicsEngine.resolveStance(entry));

        entry.facingDir = -1;
        assertEquals(BotPhysicsEngine.cfg.DEAD_LEFT_STANCE, BotPhysicsEngine.resolveStance(entry));
    }

    @Test
    void shouldUseLadderAndRopeStancesFromClimbState() {
        BotEntry ladderEntry = new BotEntry(null, null, null);
        ladderEntry.climbing = true;
        ladderEntry.climbRope = new Rope(100, 0, 40, true);

        BotEntry ropeEntry = new BotEntry(null, null, null);
        ropeEntry.climbing = true;
        ropeEntry.climbRope = new Rope(100, 0, 40, false);

        assertEquals(BotPhysicsEngine.cfg.LADDER_STANCE, BotPhysicsEngine.resolveStance(ladderEntry));
        assertEquals(BotPhysicsEngine.cfg.ROPE_STANCE, BotPhysicsEngine.resolveStance(ropeEntry));
    }

    @Test
    void shouldTickDownRopeCooldownAndDownJumpGraceInsidePhysicsEngine() {
        BotEntry entry = new BotEntry(null, null, null);
        entry.ropeGrabCooldownMs = 120;
        entry.downJumpGracePeriodMS = 120;

        BotPhysicsEngine.tickMotionTimers(entry);

        assertEquals(70, entry.ropeGrabCooldownMs);
        assertEquals(70, entry.downJumpGracePeriodMS);
        assertFalse(BotPhysicsEngine.canLand(entry));

        BotPhysicsEngine.tickMotionTimers(entry);

        assertEquals(20, entry.ropeGrabCooldownMs);
        assertEquals(20, entry.downJumpGracePeriodMS);
        assertFalse(BotPhysicsEngine.canLand(entry));

        BotPhysicsEngine.tickMotionTimers(entry);

        assertEquals(0, entry.ropeGrabCooldownMs);
        assertEquals(0, entry.downJumpGracePeriodMS);
        assertTrue(BotPhysicsEngine.canLand(entry));
    }

    @Test
    void shouldBeginFallWhenClimbDownMovesPastRopeBottom() {
        Character bot = mockBot(new Point(100, 40), null);
        BotEntry entry = new BotEntry(bot, null, null);
        Rope rope = new Rope(100, 0, 40, false);
        BotPhysicsEngine.attachToRope(entry, bot, rope, rope.bottomY());

        BotPhysicsEngine.advanceClimb(entry, bot, 1);

        assertTrue(entry.inAir);
        assertFalse(entry.climbing);
        assertEquals(new Point(100, 40), bot.getPosition());
    }

    @Test
    void shouldLandOnTopPlatformWhenHoldingAtRopeTop() {
        MapleMap map = mock(MapleMap.class);
        when(map.getPointBelow(any(Point.class))).thenAnswer(invocation -> new Point(100, 0));
        Character bot = mockBot(new Point(100, 0), map);
        BotEntry entry = new BotEntry(bot, null, null);
        Rope rope = new Rope(100, 0, 40, false);
        BotPhysicsEngine.attachToRope(entry, bot, rope, rope.topY());

        BotPhysicsEngine.holdClimb(entry, bot);

        assertFalse(entry.inAir);
        assertFalse(entry.climbing);
        assertEquals(new Point(100, 0), bot.getPosition());
        assertEquals(BotPhysicsEngine.cfg.STAND_RIGHT_STANCE, bot.getStance());
    }

    @Test
    void shouldSnapGroundMotionBackToFootholdWhenBotStartsSlightlyAboveGround() {
        MapleMap map = mock(MapleMap.class);
        when(map.getPointBelow(any(Point.class))).thenAnswer(invocation -> {
            Point probe = invocation.getArgument(0);
            return new Point(probe.x, 120);
        });
        Character bot = mockBot(new Point(100, 110), map);
        BotEntry entry = new BotEntry(bot, null, null);
        Foothold foothold = mock(Foothold.class);
        when(foothold.slope()).thenReturn(0.0);

        BotPhysicsEngine.resetMotion(entry, bot.getPosition());
        BotPhysicsEngine.GroundMotion motion = BotPhysicsEngine.applyGroundMotion(entry, bot, foothold, 0);

        assertFalse(motion.lostGround());
        assertEquals(0, motion.stepX());
        assertEquals(new Point(100, 120), bot.getPosition());
        assertFalse(entry.inAir);
    }

    @Test
    void shouldPreferExactGroundFootholdWhenOffsetLookupWouldChooseDifferentPlatform() {
        StandingLookupCase lookupCase = findStandingLookupCaseWhereOffsetDiffers(ellinia);

        assertNotNull(lookupCase, "Expected at least one standing point where offset-only lookup picks a different foothold");
        assertNotEquals(lookupCase.exactFoothold().getId(), lookupCase.offsetFoothold().getId());
        assertEquals(lookupCase.exactFoothold().getId(),
                BotPhysicsEngine.findGroundFoothold(ellinia, lookupCase.point()).getId());
    }

    @Test
    void shouldSimulateDownJumpLandingWithGraceInsteadOfImmediatePlatformBelow() {
        BotNavigationGraph graph = BotNavigationGraphProvider.rebuildGraph(ellinia);
        BotNavigationGraph.Edge dropEdge = findFirstStraightDropEdge(graph);

        assertNotNull(dropEdge, "Expected at least one straight down-jump drop edge in Ellinia");

        BotPhysicsEngine.JumpLanding landing = BotPhysicsEngine.simulateDownJumpLanding(ellinia, dropEdge.startPoint);

        assertNotNull(landing);
        assertEquals(dropEdge.endPoint, landing.point());
        assertEquals(dropEdge.toRegionId, graph.regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1));
    }

    private static StandingLookupCase findStandingLookupCaseWhereOffsetDiffers(MapleMap map) {
        for (Foothold foothold : map.getFootholds().getAllFootholds()) {
            if (foothold.isWall()) {
                continue;
            }

            int minX = Math.min(foothold.getX1(), foothold.getX2());
            int maxX = Math.max(foothold.getX1(), foothold.getX2());
            if (maxX - minX < 2) {
                continue;
            }

            for (int x = minX + 1; x < maxX; x += 4) {
                Point point = new Point(x, footingY(foothold, x));
                Foothold exact = map.getFootholds().findBelow(point);
                Foothold offset = map.getFootholds().findBelow(new Point(point.x, point.y - BotPhysicsEngine.cfg.MAX_SLOPE_UP));
                if (exact != null && offset != null && exact.getId() != offset.getId()) {
                    return new StandingLookupCase(point, exact, offset);
                }
            }
        }
        return null;
    }

    private static BotNavigationGraph.Edge findFirstStraightDropEdge(BotNavigationGraph graph) {
        for (BotNavigationGraph.Region region : graph.regions) {
            for (BotNavigationGraph.Edge edge : graph.getOutgoing(region.id)) {
                if (edge.type == BotNavigationGraph.EdgeType.DROP && edge.launchStepX == 0) {
                    return edge;
                }
            }
        }
        return null;
    }

    private static int footingY(Foothold foothold, int x) {
        if (foothold.getX1() == foothold.getX2()) {
            return Math.min(foothold.getY1(), foothold.getY2());
        }

        double ratio = (x - foothold.getX1()) / (double) (foothold.getX2() - foothold.getX1());
        return (int) Math.round(foothold.getY1() + (foothold.getY2() - foothold.getY1()) * ratio);
    }

    private static Character mockBot(Point startPosition, MapleMap map) {
        return mockBot(startPosition, map, 100);
    }

    private static Character mockBot(Point startPosition, MapleMap map, int hp) {
        Character bot = mock(Character.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(startPosition));
        AtomicInteger stance = new AtomicInteger(BotPhysicsEngine.cfg.STAND_RIGHT_STANCE);

        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getMap()).thenReturn(map);
        when(bot.getHp()).thenReturn(hp);
        when(bot.getStance()).thenAnswer(invocation -> stance.get());
        doAnswer(invocation -> {
            stance.set(invocation.getArgument(0));
            return null;
        }).when(bot).setStance(anyInt());
        return bot;
    }

    private record StandingLookupCase(Point point, Foothold exactFoothold, Foothold offsetFoothold) {
    }
}
