package server.agents.capabilities.navigation;

import server.bots.BotPhysicsEngine;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.maps.Rope;

import java.util.Map;

/**
 * Agent-owned seam for navigation-facing physics helpers while internals migrate.
 */
public final class AgentNavigationPhysicsService {
    private AgentNavigationPhysicsService() {
    }

    public static int firstClimbableY(Rope rope) {
        return BotPhysicsEngine.firstClimbableY(rope);
    }

    public static boolean isWalkableEndpointStep(int dx, int dy) {
        return BotPhysicsEngine.isWalkableEndpointStep(dx, dy);
    }

    public static void setBuildWalkRegionLookup(MapleMap map,
                                                Map<Integer, AgentNavigationGraph.Region> regionsById,
                                                Map<Integer, Integer> regionIdByFootholdId,
                                                Map<Integer, Foothold> footholdsById) {
        BotPhysicsEngine.setBuildWalkRegionLookup(map, regionsById, regionIdByFootholdId, footholdsById);
    }

    public static void clearBuildWalkRegionLookup() {
        BotPhysicsEngine.clearBuildWalkRegionLookup();
    }
}
