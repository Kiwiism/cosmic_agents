package server.agents.runtime;

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

    public static void setMapTracking(AgentRuntimeEntry entry, int mapId, Map<Integer, Foothold> footholdIndex) {
        entry.mapTrackingState().setMapTracking(mapId, footholdIndex);
    }

    public static void setMapTracking(AgentRuntimeEntry entry, MapleMap map, Map<Integer, Foothold> footholdIndex) {
        if (map != null) {
            setMapTracking(entry, map.getId(), footholdIndex);
        }
    }
}
