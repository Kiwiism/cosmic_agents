package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationMapLoader;
import server.agents.capabilities.navigation.AgentNavigationPathService;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AgentLpqStageSevenNavigationTest {
    @Test
    void authoredEntryCanReachEveryLeftSideTriggerFiringAnchor() {
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(922_010_700);
        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map);
        Point entry = new Point(-53, -895);
        int entryRegion = graph.findRegionId(map, entry);
        assertNotEquals(-1, entryRegion);

        for (Point anchor : List.of(
                new Point(-240, -990),
                new Point(-240, -1_263),
                new Point(-240, -1_469))) {
            int targetRegion = graph.findRegionId(map, anchor);
            assertNotEquals(-1, targetRegion, () -> "missing firing-anchor region " + anchor);
            assertFalse(AgentNavigationPathService.findPath(
                            graph, map, entry, entryRegion, targetRegion, anchor).isEmpty(),
                    () -> "entry cannot navigate to firing anchor " + anchor);
        }
    }
}
