package server.agents.integration;

import server.bots.BotEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary BotEntry-backed leader/owner motion
 * observation state.
 */
public final class AgentBotOwnerMotionStateRuntime {
    private AgentBotOwnerMotionStateRuntime() {
    }

    public static Point lastOwnerPosition(BotEntry entry) {
        return entry.lastOwnerPosition();
    }

    public static void rememberOwnerPosition(BotEntry entry, Point ownerPosition) {
        entry.setLastOwnerPosition(ownerPosition);
    }

    public static int observedOwnerStepX(BotEntry entry) {
        return entry.observedOwnerStepX();
    }

    public static int observedOwnerStepY(BotEntry entry) {
        return entry.observedOwnerStepY();
    }

    public static boolean observedOwnerMoved(BotEntry entry) {
        return entry.observedOwnerStepX() != 0 || entry.observedOwnerStepY() != 0;
    }

    public static int maxObservedOwnerStep(BotEntry entry) {
        return Math.max(Math.abs(entry.observedOwnerStepX()), Math.abs(entry.observedOwnerStepY()));
    }

    public static boolean ownerMostlyIdle(BotEntry entry) {
        return Math.abs(entry.observedOwnerStepX()) <= 1 && Math.abs(entry.observedOwnerStepY()) <= 1;
    }

    public static void clearObservedOwnerStep(BotEntry entry) {
        entry.setObservedOwnerStep(0, 0);
    }

    public static void updateObservedOwnerStep(BotEntry entry, Point ownerPosition) {
        Point lastOwnerPosition = entry.lastOwnerPosition();
        int stepX = lastOwnerPosition == null ? 0 : ownerPosition.x - lastOwnerPosition.x;
        int stepY = lastOwnerPosition == null ? 0 : ownerPosition.y - lastOwnerPosition.y;
        entry.setObservedOwnerStep(stepX, stepY);
    }
}
