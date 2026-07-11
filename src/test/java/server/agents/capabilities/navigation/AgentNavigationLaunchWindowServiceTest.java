package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.maps.Foothold;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationLaunchWindowServiceTest {
    @Test
    void jumpLaunchWindowRequiresJumpEdgeLaunchXAndRegionY() {
        AgentNavigationGraph graph = graphWithGroundRegion(1, 0, 100, 50);
        AgentNavigationGraph.Edge jump = edge(AgentNavigationGraph.EdgeType.JUMP,
                new Point(50, 50), new Point(100, 0), 40, 60, 0);
        int jumpY = AgentMovementPhysicsConfig.configuredJumpYThreshold();

        assertTrue(AgentNavigationLaunchWindowService.isWithinJumpLaunchWindow(
                graph, new Point(50, 50 + jumpY), jump));
        assertFalse(AgentNavigationLaunchWindowService.isWithinJumpLaunchWindow(
                graph, new Point(61, 50), jump));
        assertFalse(AgentNavigationLaunchWindowService.isWithinJumpLaunchWindow(
                graph, new Point(50, 50 + jumpY + 1), jump));
        assertFalse(AgentNavigationLaunchWindowService.isWithinJumpLaunchWindow(
                graph, new Point(50, 50), edge(AgentNavigationGraph.EdgeType.WALK,
                        new Point(50, 50), new Point(100, 0), 40, 60, 0)));
    }

    @Test
    void narrowJumpWindowExpandsToOneMotorStep() {
        AgentNavigationGraph graph = graphWithGroundRegion(1, 0, 200, 50);
        AgentNavigationGraph.Edge jump = edge(AgentNavigationGraph.EdgeType.JUMP,
                new Point(108, 50), new Point(150, 0), 108, 108, 0);

        assertFalse(AgentNavigationLaunchWindowService.isWithinJumpLaunchWindow(
                graph, new Point(104, 50), jump));
        assertTrue(AgentNavigationLaunchWindowService.isWithinJumpLaunchWindow(
                graph, new Point(104, 50), jump, 8));
        assertTrue(AgentNavigationLaunchWindowService.isWithinJumpLaunchWindow(
                graph, new Point(112, 50), jump, 8));
        assertFalse(AgentNavigationLaunchWindowService.isWithinJumpLaunchWindow(
                graph, new Point(113, 50), jump, 8));
    }

    @Test
    void dropLaunchWindowRejectsDirectionalDropsAndRopeRegions() {
        AgentNavigationGraph graph = graphWithGroundRegion(1, 0, 100, 50);
        AgentNavigationGraph.Edge straightDrop = edge(AgentNavigationGraph.EdgeType.DROP,
                new Point(50, 50), new Point(50, 150), 40, 60, 0);
        AgentNavigationGraph.Edge directionalDrop = edge(AgentNavigationGraph.EdgeType.DROP,
                new Point(50, 50), new Point(50, 150), 40, 60, 1);
        int jumpY = AgentMovementPhysicsConfig.configuredJumpYThreshold();

        assertTrue(AgentNavigationLaunchWindowService.isWithinDropLaunchWindow(
                graph, new Point(50, 50 + jumpY), straightDrop));
        assertFalse(AgentNavigationLaunchWindowService.isWithinDropLaunchWindow(
                graph, new Point(50, 50), directionalDrop));
        assertFalse(AgentNavigationLaunchWindowService.isWithinDropLaunchWindow(
                graphWithRopeRegion(1), new Point(50, 50), straightDrop));
    }

    @Test
    void dropLaunchWindowKeepsNullGraphStartPointFallback() {
        AgentNavigationGraph.Edge straightDrop = edge(AgentNavigationGraph.EdgeType.DROP,
                new Point(50, 50), new Point(50, 150), 40, 60, 0);
        int jumpY = AgentMovementPhysicsConfig.configuredJumpYThreshold();

        assertTrue(AgentNavigationLaunchWindowService.isWithinDropLaunchWindow(
                null, new Point(50, 50 + jumpY), straightDrop));
        assertFalse(AgentNavigationLaunchWindowService.isWithinDropLaunchWindow(
                null, new Point(50, 50 + jumpY + 1), straightDrop));
    }

    @Test
    void directionalDropRunwayUsesLaunchDirectionAndStartPoint() {
        AgentNavigationGraph.Edge rightDrop = edge(AgentNavigationGraph.EdgeType.DROP,
                new Point(50, 50), new Point(80, 150), 0, 0, 6);
        AgentNavigationGraph.Edge leftDrop = edge(AgentNavigationGraph.EdgeType.DROP,
                new Point(50, 50), new Point(20, 150), 0, 0, -6);
        AgentNavigationGraph.Edge straightDrop = edge(AgentNavigationGraph.EdgeType.DROP,
                new Point(50, 50), new Point(50, 150), 0, 0, 0);

        assertTrue(AgentNavigationLaunchWindowService.hasReachedDirectionalDropRunway(new Point(50, 50), rightDrop));
        assertTrue(AgentNavigationLaunchWindowService.hasReachedDirectionalDropRunway(new Point(51, 50), rightDrop));
        assertFalse(AgentNavigationLaunchWindowService.hasReachedDirectionalDropRunway(new Point(49, 50), rightDrop));

        assertTrue(AgentNavigationLaunchWindowService.hasReachedDirectionalDropRunway(new Point(50, 50), leftDrop));
        assertTrue(AgentNavigationLaunchWindowService.hasReachedDirectionalDropRunway(new Point(49, 50), leftDrop));
        assertFalse(AgentNavigationLaunchWindowService.hasReachedDirectionalDropRunway(new Point(51, 50), leftDrop));

        assertFalse(AgentNavigationLaunchWindowService.hasReachedDirectionalDropRunway(new Point(50, 50), straightDrop));
        assertFalse(AgentNavigationLaunchWindowService.hasReachedDirectionalDropRunway(null, rightDrop));
        assertFalse(AgentNavigationLaunchWindowService.hasReachedDirectionalDropRunway(new Point(50, 50), null));
    }

    private static AgentNavigationGraph graphWithGroundRegion(int regionId, int x1, int x2, int y) {
        AgentNavigationGraph.Region ground = new AgentNavigationGraph.Region(regionId, List.of(
                new AgentNavigationGraph.Segment(new Foothold(new Point(x1, y), new Point(x2, y), regionId))));
        return graph(regionId, ground);
    }

    private static AgentNavigationGraph graphWithRopeRegion(int regionId) {
        return graph(regionId, new AgentNavigationGraph.Region(regionId, 50, 0, 100, false));
    }

    private static AgentNavigationGraph graph(int regionId, AgentNavigationGraph.Region region) {
        return new AgentNavigationGraph(100,
                1,
                List.of(region),
                Map.of(regionId, region),
                Map.of(),
                Map.of(),
                Set.of());
    }

    private static AgentNavigationGraph.Edge edge(AgentNavigationGraph.EdgeType type,
                                                  Point start,
                                                  Point end,
                                                  int launchMinX,
                                                  int launchMaxX,
                                                  int launchStepX) {
        return new AgentNavigationGraph.Edge(1, 2, type,
                start, end, launchMinX, launchMaxX, launchStepX, 0, 0, 0, 0, 100);
    }
}
