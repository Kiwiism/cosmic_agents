package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed ranged retreat hold state.
 */
public final class AgentRetreatHoldStateRuntime {
    private AgentRetreatHoldStateRuntime() {
    }

    public static boolean hasHold(AgentRuntimeEntry entry) {
        return AgentRangedTacticalStateRuntime.state(entry).retreatHold().hasHold();
    }

    public static boolean hasActiveHold(AgentRuntimeEntry entry, long nowMs) {
        return AgentRangedTacticalStateRuntime.state(entry).retreatHold().active(nowMs);
    }

    public static Point holdPosition(AgentRuntimeEntry entry) {
        return AgentRangedTacticalStateRuntime.state(entry).retreatHold().position();
    }

    public static long holdUntilMs(AgentRuntimeEntry entry) {
        return AgentRangedTacticalStateRuntime.state(entry).retreatHold().untilMs();
    }

    public static int distanceFromHoldX(AgentRuntimeEntry entry, Point botPos) {
        Point hold = holdPosition(entry);
        return Math.abs(hold.x - botPos.x);
    }

    public static void setHold(AgentRuntimeEntry entry, Point position, long untilMs) {
        AgentRangedTacticalStateRuntime.state(entry).retreatHold().set(position, untilMs);
    }

    public static void clear(AgentRuntimeEntry entry) {
        AgentRangedTacticalStateRuntime.state(entry).retreatHold().clear();
    }
}
