package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed AoE reposition commitment state.
 */
public final class AgentAoeRepositionStateRuntime {
    private AgentAoeRepositionStateRuntime() {
    }

    public static Point anchor(AgentRuntimeEntry entry) {
        return AgentRangedTacticalStateRuntime.state(entry).aoeReposition().anchor();
    }

    public static boolean hasAnchor(AgentRuntimeEntry entry) {
        return AgentRangedTacticalStateRuntime.state(entry).aoeReposition().hasAnchor();
    }

    public static long deadlineMs(AgentRuntimeEntry entry) {
        return AgentRangedTacticalStateRuntime.state(entry).aoeReposition().deadlineMs();
    }

    public static void setAnchor(AgentRuntimeEntry entry, Point anchor, long deadlineMs) {
        AgentRangedTacticalStateRuntime.state(entry).aoeReposition().setAnchor(anchor, deadlineMs);
    }

    public static void clear(AgentRuntimeEntry entry) {
        AgentRangedTacticalStateRuntime.state(entry).aoeReposition().clear();
    }

    public static boolean isExpiredOrArrived(AgentRuntimeEntry entry, Point botPos, long nowMs, int arrivalX) {
        return AgentRangedTacticalStateRuntime.state(entry).aoeReposition()
                .expiredOrArrived(botPos, nowMs, arrivalX);
    }
}
