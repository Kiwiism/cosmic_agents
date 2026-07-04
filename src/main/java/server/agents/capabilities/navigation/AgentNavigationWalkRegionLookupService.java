package server.agents.capabilities.navigation;

import server.maps.Foothold;
import server.maps.MapleMap;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AgentNavigationWalkRegionLookupService {
    private static final ThreadLocal<WalkRegionLookup> ACTIVE_BUILD_WALK_REGION_LOOKUP = new ThreadLocal<>();
    private static final Map<Integer, Map<Integer, Foothold>> FOOTHOLDS_BY_ID_BY_MAP_ID = new ConcurrentHashMap<>();

    private AgentNavigationWalkRegionLookupService() {
    }

    public record WalkRegionLookup(int mapId,
                                   Map<Integer, AgentNavigationGraph.Region> regionsById,
                                   Map<Integer, Integer> regionIdByFootholdId,
                                   Map<Integer, Foothold> footholdsById) {
    }

    public static void setBuildWalkRegionLookup(MapleMap map,
                                                Map<Integer, AgentNavigationGraph.Region> regionsById,
                                                Map<Integer, Integer> regionIdByFootholdId,
                                                Map<Integer, Foothold> footholdsById) {
        if (map == null || regionsById == null || regionIdByFootholdId == null || footholdsById == null) {
            ACTIVE_BUILD_WALK_REGION_LOOKUP.remove();
            return;
        }
        ACTIVE_BUILD_WALK_REGION_LOOKUP.set(
                new WalkRegionLookup(map.getId(), regionsById, regionIdByFootholdId, footholdsById));
    }

    public static void clearBuildWalkRegionLookup() {
        ACTIVE_BUILD_WALK_REGION_LOOKUP.remove();
    }

    public static WalkRegionLookup resolveWalkRegionLookup(MapleMap map) {
        if (map == null) {
            return null;
        }

        WalkRegionLookup activeLookup = ACTIVE_BUILD_WALK_REGION_LOOKUP.get();
        if (activeLookup != null && activeLookup.mapId() == map.getId()) {
            return activeLookup;
        }

        AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(map);
        if (graph == null) {
            return null;
        }

        return new WalkRegionLookup(map.getId(), graph.regionsById, graph.regionIdByFootholdId, footholdsById(map));
    }

    private static Map<Integer, Foothold> footholdsById(MapleMap map) {
        if (map == null || map.getFootholds() == null) {
            return Map.of();
        }

        return FOOTHOLDS_BY_ID_BY_MAP_ID.computeIfAbsent(map.getId(), ignored -> {
            Map<Integer, Foothold> footholdsById = new HashMap<>();
            for (Foothold foothold : map.getFootholds().getAllFootholds()) {
                footholdsById.put(foothold.getId(), foothold);
            }
            return footholdsById;
        });
    }
}
