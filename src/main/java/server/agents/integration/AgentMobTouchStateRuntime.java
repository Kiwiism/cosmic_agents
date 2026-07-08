package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed mob-touch sweep state.
 */
public final class AgentMobTouchStateRuntime {
    private AgentMobTouchStateRuntime() {
    }

    public static Point previousCheckPositionOnMap(AgentRuntimeEntry entry, int mapId) {
        if (entry == null) {
            return null;
        }
        return entry.mobTouchState().previousCheckPositionOnMap(mapId);
    }

    public static void rememberCheck(AgentRuntimeEntry entry, Point position, int mapId) {
        entry.mobTouchState().rememberCheck(position, mapId);
    }
}
