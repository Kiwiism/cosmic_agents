package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.maps.MapleMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationProbeTest {
    @Test
    void formatsLastBuildReportForLoadedMap() {
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(100000000);

        List<String> lines = AgentNavigationProbe.lastBuildReport(map);

        assertFalse(lines.isEmpty());
    }

    @Test
    void treeThatGrewTwoBuildsBidirectionalLadderConnections() {
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(101010101);
        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map);
        List<AgentNavigationGraph.Region> ladders = graph.regions.stream()
                .filter(region -> region.isRopeRegion && region.isLadder)
                .toList();

        assertEquals(4, ladders.size(), "the authored map contains four ladder connectors");
        for (AgentNavigationGraph.Region ladder : ladders) {
            long exits = graph.getOutgoing(ladder.id).stream()
                    .filter(edge -> edge.type == AgentNavigationGraph.EdgeType.CLIMB)
                    .filter(edge -> !graph.getRegion(edge.toRegionId).isRopeRegion)
                    .count();
            long entries = graph.regions.stream()
                    .filter(region -> !region.isRopeRegion)
                    .flatMap(region -> graph.getOutgoing(region.id).stream())
                    .filter(edge -> edge.type == AgentNavigationGraph.EdgeType.CLIMB)
                    .filter(edge -> edge.toRegionId == ladder.id)
                    .count();

            assertTrue(entries >= 2, "each ladder must be enterable from both adjoining tiers");
            assertTrue(exits >= 2, "each ladder must exit onto both adjoining tiers");
        }
    }
}
