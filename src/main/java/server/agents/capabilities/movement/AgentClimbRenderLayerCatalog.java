package server.agents.capabilities.movement;

import server.maps.Rope;

import java.util.Set;

/** WZ-derived climbables whose artwork uses the client rope/foreground render layer. */
final class AgentClimbRenderLayerCatalog {
    private static final int LITH_HARBOR_MAP_ID = 104_000_000;
    private static final Set<Integer> LITH_HARBOR_FOREGROUND_LADDER_X = Set.of(
            2_114,
            2_211,
            3_318,
            4_792);

    private AgentClimbRenderLayerCatalog() {
    }

    static boolean usesClimbRenderLayer(int mapId, Rope climbable) {
        if (climbable == null) {
            return false;
        }
        if (!climbable.isLadder()) {
            return true;
        }
        return mapId == LITH_HARBOR_MAP_ID
                && LITH_HARBOR_FOREGROUND_LADDER_X.contains(climbable.x());
    }
}
