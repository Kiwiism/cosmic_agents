package server.agents.integration;

import server.bots.BotEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary BotEntry-backed stuck/unstuck movement
 * state. The legacy BotEntry fields remain the backing store during
 * reconstruction.
 */
public final class AgentBotMovementStuckStateRuntime {
    private AgentBotMovementStuckStateRuntime() {
    }

    public static int stuckMs(BotEntry entry) {
        return entry.movementStuckState().stuckMs();
    }

    public static void resetStuckMs(BotEntry entry) {
        entry.movementStuckState().setStuckMs(0);
    }

    public static void addStuckMs(BotEntry entry, int deltaMs) {
        entry.movementStuckState().addStuckMs(deltaMs);
    }

    public static int unstuckCooldownMs(BotEntry entry) {
        return entry.movementStuckState().unstuckCooldownMs();
    }

    public static boolean hasUnstuckCooldown(BotEntry entry) {
        return entry.movementStuckState().unstuckCooldownMs() > 0;
    }

    public static void setUnstuckCooldownMs(BotEntry entry, int cooldownMs) {
        entry.movementStuckState().setUnstuckCooldownMs(cooldownMs);
    }

    public static void clearStuckCheck(BotEntry entry) {
        entry.movementStuckState().clearStuckCheckPosition();
    }

    public static void resetStuckProgress(BotEntry entry) {
        resetStuckMs(entry);
        clearStuckCheck(entry);
    }

    public static boolean hasStuckCheckPosition(BotEntry entry) {
        return entry.movementStuckState().hasStuckCheckPosition();
    }

    public static void rememberStuckCheckPosition(BotEntry entry, Point position) {
        entry.movementStuckState().setStuckCheckPosition(position);
    }

    public static boolean movedSinceStuckCheck(BotEntry entry, Point position, int thresholdPx) {
        return Math.abs(position.x - entry.movementStuckState().stuckCheckX()) > thresholdPx
                || Math.abs(position.y - entry.movementStuckState().stuckCheckY()) > thresholdPx;
    }

    public static boolean stuckForAtLeast(BotEntry entry, int thresholdMs) {
        return entry.movementStuckState().stuckMs() >= thresholdMs;
    }
}
