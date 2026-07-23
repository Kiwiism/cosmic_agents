package server.agents.capabilities.townlife;

import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.maps.MapleMap;
import server.maps.Portal;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Resolves authored arrival markers onto real graph surfaces before an Agent is released. */
public final class AgentTownLifeArrivalStagingService {
    private AgentTownLifeArrivalStagingService() {
    }

    public static Point select(AgentTownLifeProfile profile,
                               MapleMap map,
                               AgentNavigationGraph graph,
                               int identitySeed) {
        if (profile == null || map == null || graph == null) {
            throw new IllegalArgumentException("town profile, map and navigation graph are required");
        }
        String preferred = profile.arrivalPortal(identitySeed);
        List<AgentTownLifeProfile.ArrivalPortal> candidates = new ArrayList<>(profile.arrivalPortals());
        candidates.sort(Comparator.comparing((AgentTownLifeProfile.ArrivalPortal portal) ->
                !portal.name().equals(preferred)));
        for (AgentTownLifeProfile.ArrivalPortal candidate : candidates) {
            Portal portal = map.getPortal(candidate.name());
            if (portal == null) {
                continue;
            }
            Point grounded = groundedPoint(graph, map, portal.getPosition());
            if (grounded != null) {
                return grounded;
            }
        }
        Portal spawnPortal = map.getRandomPlayerSpawnpoint();
        if (spawnPortal != null) {
            Point grounded = groundedPoint(graph, map, spawnPortal.getPosition());
            if (grounded != null) {
                return grounded;
            }
        }
        throw new IllegalStateException("town " + profile.mapId()
                + " has no graph-connected arrival staging point");
    }

    private static Point groundedPoint(AgentNavigationGraph graph, MapleMap map, Point point) {
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
