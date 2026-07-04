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
        return entry.ownerMotionState().lastOwnerPosition();
    }

    public static void rememberOwnerPosition(BotEntry entry, Point ownerPosition) {
        entry.ownerMotionState().setLastOwnerPosition(ownerPosition);
    }

    public static int observedOwnerStepX(BotEntry entry) {
        return entry.ownerMotionState().observedOwnerStepX();
    }

    public static int observedOwnerStepY(BotEntry entry) {
        return entry.ownerMotionState().observedOwnerStepY();
    }

    public static boolean observedOwnerMoved(BotEntry entry) {
        return entry.ownerMotionState().observedOwnerMoved();
    }

    public static int maxObservedOwnerStep(BotEntry entry) {
        return entry.ownerMotionState().maxObservedOwnerStep();
    }

    public static boolean ownerMostlyIdle(BotEntry entry) {
        return entry.ownerMotionState().ownerMostlyIdle();
    }

    public static void clearObservedOwnerStep(BotEntry entry) {
        entry.ownerMotionState().clearObservedOwnerStep();
    }

    public static void updateObservedOwnerStep(BotEntry entry, Point ownerPosition) {
        entry.ownerMotionState().updateObservedOwnerStep(ownerPosition);
    }
}
