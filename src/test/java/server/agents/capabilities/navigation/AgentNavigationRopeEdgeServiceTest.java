package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.maps.Foothold;
import server.maps.Rope;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationRopeEdgeServiceTest {
    @Test
    void topStepOffExitMatchesLegacyRopeTopWindow() {
        Rope rope = new Rope(675, 143, 215, false);
        AgentNavigationGraph.Edge topExit = edge(49, 45, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(675, 143), new Point(675, 141),
                0, 0, 0, 675, 143, 215);
        AgentNavigationGraph.Edge bottomExit = edge(49, 45, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(675, 215), new Point(675, 215),
                0, 0, 0, 675, 143, 215);

        assertTrue(AgentNavigationRopeEdgeService.isTopStepOffExit(rope, new Point(675, 145), topExit));
        assertTrue(AgentNavigationRopeEdgeService.isTopStepOffExit(rope, new Point(675, 171), topExit));
        assertFalse(AgentNavigationRopeEdgeService.isTopStepOffExit(rope, new Point(675, 215), bottomExit));
    }

    @Test
    void groundRopeJumpEntryRequiresClimbEdgeLaunchWindowAndYWindow() {
        int jumpY = AgentMovementPhysicsConfig.configuredJumpYThreshold();
        AgentNavigationGraph.Edge edge = edge(1, 2, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(100, 200), new Point(120, 120),
                90, 110, 120, 120, 220);

        assertTrue(AgentNavigationRopeEdgeService.canExecuteGroundRopeJumpEntryFromCurrentPosition(
                new Point(100, 200 + jumpY * 2), edge));
        assertFalse(AgentNavigationRopeEdgeService.canExecuteGroundRopeJumpEntryFromCurrentPosition(
                new Point(111, 200), edge));
        assertFalse(AgentNavigationRopeEdgeService.canExecuteGroundRopeJumpEntryFromCurrentPosition(
                new Point(100, 200 + jumpY * 2 + 1), edge));
        assertFalse(AgentNavigationRopeEdgeService.canExecuteGroundRopeJumpEntryFromCurrentPosition(
                new Point(100, 200), edge(1, 2, AgentNavigationGraph.EdgeType.WALK,
                        new Point(100, 200), new Point(120, 120),
                        90, 110, 120, 120, 220)));
    }

    @Test
    void ropeEntryEdgeRequiresGroundSourceAndRopeDestination() {
        AgentNavigationGraph.Region ground = new AgentNavigationGraph.Region(1, List.of(
                new AgentNavigationGraph.Segment(new Foothold(new Point(0, 50), new Point(100, 50), 1))));
        AgentNavigationGraph.Region rope = new AgentNavigationGraph.Region(2, 75, 0, 100, false);
        AgentNavigationGraph graph = new AgentNavigationGraph(100,
                1,
                List.of(ground, rope),
                Map.of(1, ground, 2, rope),
                Map.of(),
                Map.of(),
                Set.of());

        assertTrue(AgentNavigationRopeEdgeService.isRopeEntryEdge(graph,
                edge(1, 2, AgentNavigationGraph.EdgeType.CLIMB, new Point(75, 50), new Point(75, 0),
                        0, 0, 75, 0, 100)));
        assertFalse(AgentNavigationRopeEdgeService.isRopeEntryEdge(graph,
                edge(2, 1, AgentNavigationGraph.EdgeType.CLIMB, new Point(75, 0), new Point(75, 50),
                        0, 0, 75, 0, 100)));
    }

    @Test
    void topRopeJumpExitReadyOnlyAtFirstClimbableTopWindow() {
        Rope rope = new Rope(300, 100, 240, false);
        int firstClimbableY = AgentNavigationPhysicsService.firstClimbableY(rope);
        AgentNavigationGraph.Edge jumpExit = edge(2, 3, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(300, firstClimbableY), new Point(360, 90),
                0, 0, 300, 100, 240);

        assertTrue(AgentNavigationRopeEdgeService.isTopRopeJumpExitReady(
                rope, new Point(300, firstClimbableY + AgentMovementKinematicsService.climbStepPerTick() + 2), jumpExit));
        assertFalse(AgentNavigationRopeEdgeService.isTopRopeJumpExitReady(
                rope, new Point(300, firstClimbableY + AgentMovementKinematicsService.climbStepPerTick() + 3), jumpExit));
        assertFalse(AgentNavigationRopeEdgeService.isTopRopeJumpExitReady(
                rope, new Point(301, firstClimbableY), jumpExit));
        assertFalse(AgentNavigationRopeEdgeService.isTopRopeJumpExitReady(
                rope, new Point(300, firstClimbableY),
                new AgentNavigationGraph.Edge(2, 3, AgentNavigationGraph.EdgeType.CLIMB,
                        new Point(300, firstClimbableY), new Point(360, 90),
                        0, 0, 0, 0, 300, 100, 240, 100)));
    }

    @Test
    void climbExitReadinessRejectsNonClimbEdges() {
        AgentNavigationGraph graph = graph(
                new AgentNavigationGraph.Region(1, 300, 100, 240, false),
                new AgentNavigationGraph.Region(2, List.of(
                        new AgentNavigationGraph.Segment(new Foothold(new Point(300, 100), new Point(360, 100), 2)))));
        AgentNavigationGraph.Edge walk = edge(1, 2, AgentNavigationGraph.EdgeType.WALK,
                new Point(300, 100), new Point(360, 100), 0, 0, 0, 300, 100, 240);

        assertFalse(AgentNavigationRopeEdgeService.canExecuteClimbExitFromCurrentPosition(
                graph, new Point(300, 100), walk, region -> new Rope(300, 100, 240, false)));
    }

    @Test
    void climbExitReadinessAllowsRopeToRopeWithinLegacyYWindow() {
        int jumpY = AgentMovementPhysicsConfig.configuredJumpYThreshold();
        AgentNavigationGraph.Region fromRope = new AgentNavigationGraph.Region(1, 300, 100, 240, false);
        AgentNavigationGraph.Region toRope = new AgentNavigationGraph.Region(2, 360, 90, 230, false);
        AgentNavigationGraph graph = graph(fromRope, toRope);
        AgentNavigationGraph.Edge ropeToRope = edge(1, 2, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(300, 100), new Point(360, 90), 0, 0, 1, 300, 100, 240);

        assertTrue(AgentNavigationRopeEdgeService.canExecuteClimbExitFromCurrentPosition(
                graph, new Point(300, 100), ropeToRope, region -> new Rope(300, 100, 240, false)));
        assertFalse(AgentNavigationRopeEdgeService.canExecuteClimbExitFromCurrentPosition(
                graph, new Point(300, 100 + jumpY * 2 + 1), ropeToRope, region -> new Rope(300, 100, 240, false)));
    }

    @Test
    void climbExitReadinessAllowsTopStepOffOnlyAtTopExitWindow() {
        Rope rope = new Rope(300, 100, 240, false);
        AgentNavigationGraph graph = graph(
                new AgentNavigationGraph.Region(1, 300, 100, 240, false),
                new AgentNavigationGraph.Region(2, List.of(
                        new AgentNavigationGraph.Segment(new Foothold(new Point(300, 98), new Point(360, 98), 2)))));
        AgentNavigationGraph.Edge topStepOff = edge(1, 2, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(300, 100), new Point(300, 98), 0, 0, 0, 300, 100, 240);

        assertTrue(AgentNavigationRopeEdgeService.canExecuteClimbExitFromCurrentPosition(
                graph, new Point(300, 102), topStepOff, region -> rope));
        assertFalse(AgentNavigationRopeEdgeService.canExecuteClimbExitFromCurrentPosition(
                graph, new Point(300, 240), topStepOff, region -> rope));
    }

    @Test
    void climbExitReadinessRequiresAuthoredRopeJumpStartHeight() {
        Rope rope = new Rope(300, 100, 240, false);
        int firstClimbableY = AgentNavigationPhysicsService.firstClimbableY(rope);
        AgentNavigationGraph graph = graph(
                new AgentNavigationGraph.Region(1, 300, 100, 240, false),
                new AgentNavigationGraph.Region(2, List.of(
                        new AgentNavigationGraph.Segment(new Foothold(new Point(330, 90), new Point(390, 90), 2)))));
        AgentNavigationGraph.Edge jumpExit = edge(1, 2, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(300, firstClimbableY), new Point(360, 90), 0, 0, 1, 300, 100, 240);

        assertTrue(AgentNavigationRopeEdgeService.canExecuteClimbExitFromCurrentPosition(
                graph, new Point(300, firstClimbableY + AgentMovementKinematicsService.climbStepPerTick() + 2),
                jumpExit, region -> rope));
        assertFalse(AgentNavigationRopeEdgeService.canExecuteClimbExitFromCurrentPosition(
                graph, new Point(300, firstClimbableY + AgentMovementKinematicsService.climbStepPerTick() + 3),
                jumpExit, region -> rope));
    }

    private static AgentNavigationGraph.Edge edge(int fromRegionId,
                                                  int toRegionId,
                                                  AgentNavigationGraph.EdgeType type,
                                                  Point start,
                                                  Point end,
                                                  int launchMinX,
                                                  int launchMaxX,
                                                  int ropeX,
                                                  int ropeTopY,
                                                  int ropeBottomY) {
        return new AgentNavigationGraph.Edge(fromRegionId, toRegionId, type,
                start, end, launchMinX, launchMaxX, 1, 0, ropeX, ropeTopY, ropeBottomY, 100);
    }

    private static AgentNavigationGraph.Edge edge(int fromRegionId,
                                                  int toRegionId,
                                                  AgentNavigationGraph.EdgeType type,
                                                  Point start,
                                                  Point end,
                                                  int launchMinX,
                                                  int launchMaxX,
                                                  int launchStepX,
                                                  int ropeX,
                                                  int ropeTopY,
                                                  int ropeBottomY) {
        return new AgentNavigationGraph.Edge(fromRegionId, toRegionId, type,
                start, end, launchMinX, launchMaxX, launchStepX, 0, ropeX, ropeTopY, ropeBottomY, 100);
    }

    private static AgentNavigationGraph graph(AgentNavigationGraph.Region... regions) {
        Map<Integer, AgentNavigationGraph.Region> regionsById = new java.util.HashMap<>();
        for (AgentNavigationGraph.Region region : regions) {
            regionsById.put(region.id, region);
        }
        return new AgentNavigationGraph(100,
                1,
                List.of(regions),
                regionsById,
                Map.of(),
                Map.of(),
                Set.of());
    }
}
