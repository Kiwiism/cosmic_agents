package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed AoE reposition commitment state.
 */
public final class AgentAoeRepositionStateRuntime {
    private AgentAoeRepositionStateRuntime() {
    }

    public static Point anchor(AgentRuntimeEntry entry) {
        return entry.aoeRepositionState().anchor();
    }

    public static boolean hasAnchor(AgentRuntimeEntry entry) {
        return entry.aoeRepositionState().hasAnchor();
    }

    public static long deadlineMs(AgentRuntimeEntry entry) {
        return entry.aoeRepositionState().deadlineMs();
    }

    public static void setAnchor(AgentRuntimeEntry entry, Point anchor, long deadlineMs) {
        entry.aoeRepositionState().setAnchor(anchor, deadlineMs);
    }

    public static void clear(AgentRuntimeEntry entry) {
        entry.aoeRepositionState().clear();
    }

    public static boolean isExpiredOrArrived(AgentRuntimeEntry entry, Point botPos, long nowMs, int arrivalX) {
        return entry.aoeRepositionState().expiredOrArrived(botPos, nowMs, arrivalX);
    }
}
