package server.agents.capabilities.movement;

import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.util.Map;

/**
 * Agent-owned adapter for AgentRuntimeEntry-backed map/foothold tracking state.
 */
public final class AgentMapStateRuntime {
    private AgentMapStateRuntime() {
    }

    public static int lastMapId(AgentRuntimeEntry entry) {
        return entry.mapTrackingState().lastMapId();
    }

    public static boolean isTrackingMap(AgentRuntimeEntry entry, int mapId) {
        return lastMapId(entry) == mapId;
    }

    public static boolean isTrackingMap(AgentRuntimeEntry entry, MapleMap map) {
        return map != null && isTrackingMap(entry, map.getId());
    }

    public static Map<Integer, Foothold> footholdIndex(AgentRuntimeEntry entry) {
        return entry.mapTrackingState().footholdIndex();
    }

    public static int entryPortalId(AgentRuntimeEntry entry) {
        return entry.mapTrackingState().entryPortalId();
    }

    public static void setEntryPortalId(AgentRuntimeEntry entry, int portalId) {
        entry.mapTrackingState().setEntryPortalId(portalId);
    }

    public static void setMapTracking(AgentRuntimeEntry entry, int mapId, Map<Integer, Foothold> footholdIndex) {
        entry.mapTrackingState().setMapTracking(mapId, footholdIndex);
    }

    public static void setMapTracking(AgentRuntimeEntry entry, MapleMap map, Map<Integer, Foothold> footholdIndex) {
        if (map != null) {
            setMapTracking(entry, map.getId(), footholdIndex);
        }
    }
}
