package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed ranged retreat hold state.
 */
public final class AgentBotRetreatHoldStateRuntime {
    private AgentBotRetreatHoldStateRuntime() {
    }

    public static boolean hasHold(AgentRuntimeEntry entry) {
        return entry.retreatHoldState().hasHold();
    }

    public static boolean hasActiveHold(AgentRuntimeEntry entry, long nowMs) {
        return entry.retreatHoldState().active(nowMs);
    }

    public static Point holdPosition(AgentRuntimeEntry entry) {
        return entry.retreatHoldState().position();
    }

    public static long holdUntilMs(AgentRuntimeEntry entry) {
        return entry.retreatHoldState().untilMs();
    }

    public static int distanceFromHoldX(AgentRuntimeEntry entry, Point botPos) {
        Point hold = holdPosition(entry);
        return Math.abs(hold.x - botPos.x);
    }

    public static void setHold(AgentRuntimeEntry entry, Point position, long untilMs) {
        entry.retreatHoldState().set(position, untilMs);
    }

    public static void clear(AgentRuntimeEntry entry) {
        entry.retreatHoldState().clear();
    }
}
