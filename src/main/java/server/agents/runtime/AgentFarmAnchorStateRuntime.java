package server.agents.runtime;

import java.awt.Point;

/**
 * Agent-owned adapter for AgentRuntimeEntry-backed sentry/farm anchor state.
 */
public final class AgentFarmAnchorStateRuntime {
    private AgentFarmAnchorStateRuntime() {
    }

    public static Point farmAnchor(AgentRuntimeEntry entry) {
        return entry.farmAnchorState().anchor();
    }

    public static int farmAnchorMapId(AgentRuntimeEntry entry) {
        return entry.farmAnchorState().mapId();
    }

    public static boolean hasFarmAnchor(AgentRuntimeEntry entry) {
        return entry.farmAnchorState().hasAnchor();
    }

    public static boolean isFarmAnchorInMap(AgentRuntimeEntry entry, int mapId) {
        return hasFarmAnchor(entry) && farmAnchorMapId(entry) == mapId;
    }

    public static Point farmAnchorInMap(AgentRuntimeEntry entry, int mapId) {
        return isFarmAnchorInMap(entry, mapId) ? farmAnchor(entry) : null;
    }

    public static void setFarmAnchor(AgentRuntimeEntry entry, Point anchor, int mapId) {
        entry.farmAnchorState().setAnchor(anchor, mapId);
    }

    public static void clearFarmAnchor(AgentRuntimeEntry entry) {
        entry.farmAnchorState().clear();
    }

    public static boolean clearFarmAnchorIfMapChanged(AgentRuntimeEntry entry, int mapId) {
        if (!hasFarmAnchor(entry) || farmAnchorMapId(entry) == mapId) {
            return false;
        }
        clearFarmAnchor(entry);
        return true;
    }
}
