package server.agents.capabilities.movement;

import server.maps.Rope;

/** WZ-derived climbables whose artwork uses the client rope/foreground render layer. */
final class AgentClimbRenderLayerCatalog {
    private static final int LITH_HARBOR_MAP_ID = 104_000_000;

    private AgentClimbRenderLayerCatalog() {
    }

    static boolean usesClimbRenderLayer(int mapId, Rope climbable) {
        if (climbable == null) {
            return false;
        }
        if (!climbable.isLadder()) {
            return true;
        }
        // Lith Harbor's WZ uses foreground artwork for every ladder, including the short
        // platform ladders that were omitted by the original four-X exception list.
        return mapId == LITH_HARBOR_MAP_ID;
    }
}
