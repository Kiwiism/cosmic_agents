package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.maps.Rope;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentClimbMovementPolicyTest {
    @Test
    void shouldNotHoldClimbIdleWhileNavigationEdgeIsActive() {
        assertFalse(AgentClimbMovementPolicy.shouldHoldClimbIdle(true, false, 0, 0, 100, 150));
    }

    @Test
    void shouldNotHoldClimbIdleWhileGrinding() {
        assertFalse(AgentClimbMovementPolicy.shouldHoldClimbIdle(false, true, 0, 0, 100, 150));
    }

    @Test
    void shouldHoldClimbIdleWhenCloseToLeaderAndNotGrinding() {
        assertTrue(AgentClimbMovementPolicy.shouldHoldClimbIdle(false, false, 99, 299, 100, 150));
    }

    @Test
    void shouldNotHoldClimbIdleOutsideStopOrFollowRange() {
        assertFalse(AgentClimbMovementPolicy.shouldHoldClimbIdle(false, false, 100, 0, 100, 150));
        assertFalse(AgentClimbMovementPolicy.shouldHoldClimbIdle(false, false, 0, 300, 100, 150));
    }

    @Test
    void shouldRejectInvalidClimbSnapInputs() {
        Rope rope = new Rope(10, 100, 300, false);
        Point target = new Point(10, 200);

        assertFalse(AgentClimbMovementPolicy.shouldSnapToClimbTarget(false, rope, target, 1, true, 8));
        assertFalse(AgentClimbMovementPolicy.shouldSnapToClimbTarget(true, null, target, 1, true, 8));
        assertFalse(AgentClimbMovementPolicy.shouldSnapToClimbTarget(true, rope, null, 1, true, 8));
        assertFalse(AgentClimbMovementPolicy.shouldSnapToClimbTarget(true, rope, target, 0, true, 8));
        assertFalse(AgentClimbMovementPolicy.shouldSnapToClimbTarget(true, rope, target, 1, false, 8));
    }

    @Test
    void shouldRejectClimbSnapOutsideCurrentRopeBounds() {
        Rope rope = new Rope(10, 100, 300, false);

        assertFalse(AgentClimbMovementPolicy.shouldSnapToClimbTarget(true, rope, new Point(11, 200), 1, true, 8));
        assertFalse(AgentClimbMovementPolicy.shouldSnapToClimbTarget(true, rope, new Point(10, 100), 1, true, 8));
        assertFalse(AgentClimbMovementPolicy.shouldSnapToClimbTarget(true, rope, new Point(10, 99), 1, true, 8));
        assertFalse(AgentClimbMovementPolicy.shouldSnapToClimbTarget(true, rope, new Point(10, 301), 1, true, 8));
    }

    @Test
    void shouldAllowClimbSnapAtRopeBottomWhenWithinSingleClimbStep() {
        Rope rope = new Rope(10, 100, 300, false);

        assertTrue(AgentClimbMovementPolicy.shouldSnapToClimbTarget(true, rope, new Point(10, 300), 7, true, 8));
    }

    @Test
    void shouldRejectClimbSnapWhenDeltaReachesClimbStep() {
        Rope rope = new Rope(10, 100, 300, false);

        assertFalse(AgentClimbMovementPolicy.shouldSnapToClimbTarget(true, rope, new Point(10, 300), 8, true, 8));
    }

    @Test
    void shouldMatchEquivalentRopes() {
        assertTrue(AgentClimbMovementPolicy.sameRope(
                new Rope(10, 100, 300, false),
                new Rope(10, 100, 300, false)));
    }

    @Test
    void shouldRejectDifferentOrMissingRopes() {
        Rope rope = new Rope(10, 100, 300, false);

        assertFalse(AgentClimbMovementPolicy.sameRope(null, rope));
        assertFalse(AgentClimbMovementPolicy.sameRope(rope, null));
        assertFalse(AgentClimbMovementPolicy.sameRope(rope, new Rope(11, 100, 300, false)));
        assertFalse(AgentClimbMovementPolicy.sameRope(rope, new Rope(10, 99, 300, false)));
        assertFalse(AgentClimbMovementPolicy.sameRope(rope, new Rope(10, 100, 301, false)));
        assertFalse(AgentClimbMovementPolicy.sameRope(rope, new Rope(10, 100, 300, true)));
    }
}
