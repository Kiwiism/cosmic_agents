package server.bots;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.*;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals(BotPhysicsEngine.cfg.STAND_STANCE, BotPhysicsEngine.resolveStance(entry));
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
}
