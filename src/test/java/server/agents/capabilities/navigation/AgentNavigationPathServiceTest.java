package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.maps.Foothold;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentNavigationPathServiceTest {
    @Test
    void intraRegionTravelCostUsesWalkVelocityForGroundRegion() {
        AgentNavigationGraph graph = graphWithRegion(
                new AgentNavigationGraph.Region(1, List.of(new AgentNavigationGraph.Segment(
                        new Foothold(new Point(0, 100), new Point(100, 100), 1)))));

        int expected = Math.max(0, (int) Math.round((50 * 1000.0) / Math.max(1.0, graph.movementProfile.walkVelocityPxs())));

        assertEquals(expected, AgentNavigationPathService.intraRegionTravelCost(graph, 1, new Point(0, 100), new Point(50, 100)));
        assertEquals(expected, AgentNavigationPathService.heuristic(graph, new Point(0, 100), new Point(50, 100)));
    }

    @Test
    void intraRegionTravelCostUsesClimbSpeedForRopeRegion() {
        AgentNavigationGraph graph = graphWithRegion(new AgentNavigationGraph.Region(1, 100, 50, 250, false));
        int expected = Math.max(0, (int) Math.round((75 * 1000.0) / Math.max(1, AgentMovementPhysicsConfig.configuredClimbSpeedPxs())));

        assertEquals(expected, AgentNavigationPathService.intraRegionTravelCost(graph, 1, new Point(100, 50), new Point(100, 125)));
    }

    private static AgentNavigationGraph graphWithRegion(AgentNavigationGraph.Region region) {
        return new AgentNavigationGraph(
                1,
                1,
                AgentMovementProfile.base(),
                List.of(region),
                Map.of(region.id, region),
                Map.of(),
                Map.of(),
                Set.of());
    }
}
