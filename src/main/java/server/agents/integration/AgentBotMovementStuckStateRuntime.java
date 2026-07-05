package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary BotEntry-backed stuck/unstuck movement
 * state. The legacy BotEntry fields remain the backing store during
 * reconstruction.
 */
public final class AgentBotMovementStuckStateRuntime {
    private AgentBotMovementStuckStateRuntime() {
    }

    public static int stuckMs(AgentRuntimeEntry entry) {
        return entry.movementStuckState().stuckMs();
    }

    public static void resetStuckMs(AgentRuntimeEntry entry) {
        entry.movementStuckState().setStuckMs(0);
    }

    public static void addStuckMs(AgentRuntimeEntry entry, int deltaMs) {
        entry.movementStuckState().addStuckMs(deltaMs);
    }

    public static int unstuckCooldownMs(AgentRuntimeEntry entry) {
        return entry.movementStuckState().unstuckCooldownMs();
    }

    public static boolean hasUnstuckCooldown(AgentRuntimeEntry entry) {
        return entry.movementStuckState().unstuckCooldownMs() > 0;
    }

    public static void setUnstuckCooldownMs(AgentRuntimeEntry entry, int cooldownMs) {
        entry.movementStuckState().setUnstuckCooldownMs(cooldownMs);
    }

    public static void clearStuckCheck(AgentRuntimeEntry entry) {
        entry.movementStuckState().clearStuckCheckPosition();
    }

    public static void resetStuckProgress(AgentRuntimeEntry entry) {
        resetStuckMs(entry);
        clearStuckCheck(entry);
    }

    public static boolean hasStuckCheckPosition(AgentRuntimeEntry entry) {
        return entry.movementStuckState().hasStuckCheckPosition();
    }

    public static void rememberStuckCheckPosition(AgentRuntimeEntry entry, Point position) {
        entry.movementStuckState().setStuckCheckPosition(position);
    }

    public static boolean movedSinceStuckCheck(AgentRuntimeEntry entry, Point position, int thresholdPx) {
        return Math.abs(position.x - entry.movementStuckState().stuckCheckX()) > thresholdPx
                || Math.abs(position.y - entry.movementStuckState().stuckCheckY()) > thresholdPx;
    }

    public static boolean stuckForAtLeast(AgentRuntimeEntry entry, int thresholdMs) {
        return entry.movementStuckState().stuckMs() >= thresholdMs;
    }
}
