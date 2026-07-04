package server.agents.capabilities.navigation;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.bots.BotPhysicsEngine;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.maps.Rope;

import java.util.Map;

/**
 * Agent-owned seam for navigation-facing physics helpers while internals migrate.
 */
public final class AgentNavigationPhysicsService {
    private static final int WALK_GAP_PX = 12;

    private AgentNavigationPhysicsService() {
    }

    public static int firstClimbableY(Rope rope) {
        return Math.min(rope.bottomY(), rope.topY() + 1);
    }

    public static boolean isWalkableEndpointStep(int dx, int dy) {
        return dx <= WALK_GAP_PX
                && dy <= AgentMovementPhysicsConfig.configuredMaxSnapDrop()
                && dy >= -AgentMovementPhysicsConfig.configuredMaxSlopeUp();
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
