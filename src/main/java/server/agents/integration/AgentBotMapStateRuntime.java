package server.agents.integration;

import server.bots.BotEntry;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.util.Map;

/**
 * Agent-owned adapter for temporary BotEntry-backed map/foothold tracking state.
 */
public final class AgentBotMapStateRuntime {
    private AgentBotMapStateRuntime() {
    }

    public static int lastMapId(BotEntry entry) {
        return entry.mapTrackingState().lastMapId();
    }

    public static boolean isTrackingMap(BotEntry entry, int mapId) {
        return lastMapId(entry) == mapId;
    }

    public static boolean isTrackingMap(BotEntry entry, MapleMap map) {
        return map != null && isTrackingMap(entry, map.getId());
    }

    public static Map<Integer, Foothold> footholdIndex(BotEntry entry) {
        return entry.mapTrackingState().footholdIndex();
    }

    public static void setMapTracking(BotEntry entry, int mapId, Map<Integer, Foothold> footholdIndex) {
        entry.mapTrackingState().setMapTracking(mapId, footholdIndex);
    }

    public static void setMapTracking(BotEntry entry, MapleMap map, Map<Integer, Foothold> footholdIndex) {
        if (map != null) {
            setMapTracking(entry, map.getId(), footholdIndex);
        }
    }
}
