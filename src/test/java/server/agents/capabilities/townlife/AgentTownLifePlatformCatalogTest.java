package server.agents.capabilities.townlife;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.maps.Foothold;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTownLifePlatformCatalogTest {
    @Test
    void catalogsUpperLowerAndMiniPlatformsAsIndependentReservationSlots() {
        AgentNavigationGraph.Region upper = region(1, 1, 0, 100, 640, 100);
        AgentNavigationGraph.Region lower = region(2, 2, 0, 650, 640, 650);
        AgentNavigationGraph.Region mini = region(3, 3, 800, 380, 960, 380);
        List<AgentNavigationGraph.Region> regions = List.of(upper, lower, mini);
        AgentNavigationGraph graph = new AgentNavigationGraph(
                LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID, 1, regions,
                Map.of(1, upper, 2, lower, 3, mini), Map.of(), Map.of(), Set.of());
        AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                .require(LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID);

        List<AgentTownLifePlatformCatalog.PlatformSpot> spots =
                AgentTownLifePlatformCatalog.reachable(graph, profile, -1);

        assertTrue(spots.stream().anyMatch(spot ->
                spot.district() == AgentTownLifeState.District.UPPER));
        assertTrue(spots.stream().anyMatch(spot ->
                spot.district() == AgentTownLifeState.District.LOWER));
        assertTrue(spots.stream().anyMatch(spot ->
                spot.platformKind() == AgentTownLifeState.PlatformKind.MINI));
        assertEquals(8, spots.stream().filter(spot -> spot.space().rowId() == 1).count());
    }

    private static AgentNavigationGraph.Region region(int regionId,
                                                       int footholdId,
                                                       int x1,
                                                       int y1,
                                                       int x2,
                                                       int y2) {
        Foothold foothold = new Foothold(new Point(x1, y1), new Point(x2, y2), footholdId);
        return new AgentNavigationGraph.Region(
                regionId, List.of(new AgentNavigationGraph.Segment(foothold)));
    }
}
