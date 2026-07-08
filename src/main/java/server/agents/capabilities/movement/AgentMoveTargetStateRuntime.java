package server.agents.capabilities.movement;

import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for AgentRuntimeEntry-backed explicit movement targets.
 */
public final class AgentMoveTargetStateRuntime {
    private AgentMoveTargetStateRuntime() {
    }

    public static Point moveTarget(AgentRuntimeEntry entry) {
        return entry.moveTargetState().target();
    }

    public static boolean hasMoveTarget(AgentRuntimeEntry entry) {
        return entry.moveTargetState().hasTarget();
    }

    public static boolean isPrecise(AgentRuntimeEntry entry) {
        return entry.moveTargetState().precise();
    }

    public static void setMoveTarget(AgentRuntimeEntry entry, Point target, boolean precise) {
        entry.moveTargetState().setTarget(target, precise);
    }

    public static void setPreciseMoveTarget(AgentRuntimeEntry entry, Point target) {
        setMoveTarget(entry, target, true);
    }

    public static void clearMoveTarget(AgentRuntimeEntry entry) {
        entry.moveTargetState().clear();
    }

    public static boolean moveTargetEquals(AgentRuntimeEntry entry, Point point) {
        return entry.moveTargetState().targetEquals(point);
    }

    public static boolean hasReachedMoveTarget(AgentRuntimeEntry entry, Point currentPosition, int normalArrivalDistance) {
        Point target = moveTarget(entry);
        if (target == null || currentPosition == null) {
            return false;
        }
        int arrivalDistance = isPrecise(entry) ? 8 : normalArrivalDistance;
        return Math.abs(currentPosition.x - target.x) <= arrivalDistance
                && Math.abs(currentPosition.y - target.y) <= arrivalDistance;
    }
}
