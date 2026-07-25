package server.agents.capabilities.navigation;

import server.maps.MapleMap;
import server.maps.Portal;

import java.awt.Point;
import java.util.List;

/** Resolves ordered arrival portals onto real, non-rope navigation surfaces. */
public final class AgentArrivalStagingService {
    private AgentArrivalStagingService() {
    }

    public static Point select(MapleMap map,
                               AgentNavigationGraph graph,
                               List<String> orderedPortalNames) {
        return select(map, graph, orderedPortalNames, true);
    }

    public static Point select(MapleMap map,
                               AgentNavigationGraph graph,
                               List<String> orderedPortalNames,
                               boolean allowRandomSpawnFallback) {
        if (map == null || graph == null || orderedPortalNames == null) {
            throw new IllegalArgumentException(
                    "map, navigation graph and ordered arrival portals are required");
        }
        for (String portalName : orderedPortalNames) {
            if (portalName == null || portalName.isBlank()) {
                continue;
            }
            Portal portal = map.getPortal(portalName);
            Point grounded = portal == null
                    ? null : groundedPoint(graph, map, portal.getPosition());
            if (grounded != null) {
                return grounded;
            }
        }
        if (allowRandomSpawnFallback) {
            Portal spawnPortal = map.getRandomPlayerSpawnpoint();
            if (spawnPortal != null) {
                Point grounded = groundedPoint(graph, map, spawnPortal.getPosition());
                if (grounded != null) {
                    return grounded;
                }
            }
        }
        throw new IllegalStateException(
                "map " + map.getId() + " has no graph-connected arrival staging point");
    }

    private static Point groundedPoint(
            AgentNavigationGraph graph, MapleMap map, Point point) {
        if (point == null) {
            return null;
        }
        int regionId = graph.findRegionId(map, point);
        AgentNavigationGraph.Region region = graph.getRegion(regionId);
        if (region == null || region.isRopeRegion) {
            return null;
        }
        return region.pointAt(point.x);
    }
}
