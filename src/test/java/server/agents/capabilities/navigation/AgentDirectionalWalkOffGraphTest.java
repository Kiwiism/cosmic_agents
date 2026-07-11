package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentJumpProbeService;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.movement.AgentWalkOffLanding;
import server.maps.MapleMap;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AgentDirectionalWalkOffGraphTest {
    @Test
    void henesysStoreDirectionalDropsUseExecutionWalkOffLanding() {
        assumeTrue(Files.isDirectory(Path.of("wz", "Map.wz")), "wz/Map.wz not present");
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(100000102);
        AgentMovementProfile profile = AgentMovementProfile.base();
        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map, profile);
        int checked = 0;

        for (AgentNavigationGraph.Region region : graph.regions) {
            for (AgentNavigationGraph.Edge edge : graph.getOutgoing(region.id)) {
                if (edge.type != AgentNavigationGraph.EdgeType.DROP || edge.launchStepX == 0) {
                    continue;
                }
                AgentWalkOffLanding walkOff = AgentJumpProbeService.simulateWalkOffLanding(
                        map, edge.startPoint, Integer.signum(edge.launchStepX), profile);
                assertTrue(walkOff != null && walkOff.landing() != null && walkOff.landing().foothold() != null);
                int landingRegion = graph.regionIdByFootholdId.getOrDefault(
                        walkOff.landing().foothold().getId(), -1);
                assertEquals(edge.toRegionId, landingRegion,
                        "directional DROP must be authored from its executable walk-off outcome");
                checked++;
            }
        }
        assertTrue(checked > 0);
    }
}
