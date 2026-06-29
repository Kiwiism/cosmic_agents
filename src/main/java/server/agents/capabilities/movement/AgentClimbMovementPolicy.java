package server.agents.capabilities.movement;

import java.awt.Point;
import server.maps.Rope;

public final class AgentClimbMovementPolicy {
    private AgentClimbMovementPolicy() {
    }

    public static boolean shouldHoldClimbIdle(boolean hasActiveNavigationEdge,
                                              boolean grinding,
                                              int dy,
                                              int dxOwner,
                                              int stopDist,
                                              int followDist) {
        if (hasActiveNavigationEdge) {
            return false;
        }
        return !grinding
                && Math.abs(dy) < stopDist
                && Math.abs(dxOwner) < followDist * 2;
    }

    public static boolean shouldSnapToClimbTarget(boolean climbing,
                                                  Rope climbRope,
                                                  Point targetPos,
                                                  int dy,
                                                  boolean preciseTarget,
                                                  int climbStepPerTick) {
        if (!climbing || climbRope == null || targetPos == null || dy == 0) {
            return false;
        }
        if (!preciseTarget) {
            return false;
        }
        if (targetPos.x != climbRope.x()) {
            return false;
        }
        if (targetPos.y <= climbRope.topY() || targetPos.y > climbRope.bottomY()) {
            return false;
        }
        return Math.abs(dy) < climbStepPerTick;
    }

    public static boolean sameRope(Rope left, Rope right) {
        return left != null && right != null
                && left.x() == right.x()
                && left.topY() == right.topY()
                && left.bottomY() == right.bottomY()
                && left.isLadder() == right.isLadder();
    }
}
