package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.maps.Foothold;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AgentNavigationGrindTargetServiceTest {
    @Test
    void returnsRawTargetWhenNotGrindingOrTargetInvalid() {
        AgentNavigationGraph graph = graphWithRegion(1, 0, 100, 50);
        Point target = new Point(-200, 50);

        assertSame(target, AgentNavigationGrindTargetService.adjustPathTarget(false, graph, 1, target));
        assertSame(target, AgentNavigationGrindTargetService.adjustPathTarget(true, graph, -1, target));
        assertSame(target, AgentNavigationGrindTargetService.adjustPathTarget(true, graph, 99, target));
        assertSame(target, AgentNavigationGrindTargetService.adjustPathTarget(true, graph, 2, target));
    }

    @Test
    void clampsGroundTargetInsideGrindEdgeMargin() {
        AgentNavigationGraph graph = graphWithRegion(1, 0, 100, 50);
        int margin = AgentMovementPhysicsConfig.configuredGrindEdgeMargin();

        assertEquals(new Point(margin, 50),
                AgentNavigationGrindTargetService.adjustPathTarget(true, graph, 1, new Point(-200, 50)));
        assertEquals(new Point(100 - margin, 50),
                AgentNavigationGrindTargetService.adjustPathTarget(true, graph, 1, new Point(200, 50)));
        assertEquals(new Point(50, 50),
                AgentNavigationGrindTargetService.adjustPathTarget(true, graph, 1, new Point(50, 50)));
    }

    @Test
    void returnsRawTargetWhenMarginConsumesRegion() {
        AgentNavigationGraph graph = graphWithRegion(1, 0, 10, 50);
        Point target = new Point(-200, 50);

        assertSame(target, AgentNavigationGrindTargetService.adjustPathTarget(true, graph, 1, target));
    }

    private static AgentNavigationGraph graphWithRegion(int regionId, int x1, int x2, int y) {
        AgentNavigationGraph.Region ground = new AgentNavigationGraph.Region(regionId, List.of(
                new AgentNavigationGraph.Segment(new Foothold(new Point(x1, y), new Point(x2, y), regionId))));
        AgentNavigationGraph.Region rope = new AgentNavigationGraph.Region(2, 75, 0, 100, false);
        return new AgentNavigationGraph(100,
                1,
                List.of(ground, rope),
                Map.of(regionId, ground, 2, rope),
                Map.of(),
                Map.of(),
                Set.of());
    }
}
