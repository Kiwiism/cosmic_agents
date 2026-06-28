package server.agents.integration;

import server.bots.BotEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary BotEntry-backed explicit movement targets.
 */
public final class AgentBotMoveTargetStateRuntime {
    private AgentBotMoveTargetStateRuntime() {
    }

    public static Point moveTarget(BotEntry entry) {
        return entry.moveTarget();
    }

    public static boolean hasMoveTarget(BotEntry entry) {
        return entry.hasMoveTarget();
    }

    public static boolean isPrecise(BotEntry entry) {
        return entry.isMoveTargetPrecise();
    }

    public static void setMoveTarget(BotEntry entry, Point target, boolean precise) {
        entry.setMoveTarget(target, precise);
    }

    public static void setPreciseMoveTarget(BotEntry entry, Point target) {
        setMoveTarget(entry, target, true);
    }

    public static void clearMoveTarget(BotEntry entry) {
        entry.clearMoveTarget();
    }

    public static boolean moveTargetEquals(BotEntry entry, Point point) {
        return entry.moveTargetEquals(point);
    }

    public static boolean hasReachedMoveTarget(BotEntry entry, Point currentPosition, int normalArrivalDistance) {
        Point target = moveTarget(entry);
        if (target == null || currentPosition == null) {
            return false;
        }
        int arrivalDistance = isPrecise(entry) ? 8 : normalArrivalDistance;
        return Math.abs(currentPosition.x - target.x) <= arrivalDistance
                && Math.abs(currentPosition.y - target.y) <= arrivalDistance;
    }
}
