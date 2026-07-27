package server.agents.capabilities.movement;

import constants.id.MapId;
import server.maps.Rope;

/**
 * Selects climbables that use the client rope/foreground render layer.
 *
 * <p>Ladders use that layer by default. Map families whose native ladder artwork
 * already renders correctly belong in the exclusion policy below.</p>
 */
final class AgentClimbRenderLayerCatalog {
    private AgentClimbRenderLayerCatalog() {
    }

    static boolean usesClimbRenderLayer(int mapId, Rope climbable) {
        if (climbable == null) {
            return false;
        }
        if (!climbable.isLadder()) {
            return true;
        }
        return !isExcludedLadderMap(mapId);
    }

    private static boolean isExcludedLadderMap(int mapId) {
        return MapId.isMapleIsland(mapId);
    }
}
