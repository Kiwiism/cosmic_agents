package server.agents.capabilities.navigation;

import server.agents.capabilities.movement.AgentMovementProfile;
import server.maps.MapleMap;

import java.awt.Point;

/** Resolves a patrol point behind one navigation-owned query seam. */
public final class AgentNavigationPatrolRegionService {
    private AgentNavigationPatrolRegionService() {
    }

    public static int resolveRegionId(
            MapleMap map, Point position, AgentMovementProfile profile) {
        if (map == null || position == null || profile == null) {
            return -1;
        }
        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(map, profile);
        return graph == null ? -1 : graph.findRegionId(map, position);
    }
}
