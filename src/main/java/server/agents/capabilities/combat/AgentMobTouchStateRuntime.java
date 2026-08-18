package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/**
 * Capability-owned adapter for mob-touch sweep state.
 */
public final class AgentMobTouchStateRuntime {
    private AgentMobTouchStateRuntime() {
    }

    public static Point previousCheckPositionOnMap(AgentRuntimeEntry entry, int mapId) {
        if (entry == null) {
            return null;
        }
        return entry.capabilityStates().require(AgentMobTouchState.STATE_KEY).previousCheckPositionOnMap(mapId);
    }

    public static void rememberCheck(AgentRuntimeEntry entry, Point position, int mapId) {
        entry.capabilityStates().require(AgentMobTouchState.STATE_KEY).rememberCheck(position, mapId);
    }
}
