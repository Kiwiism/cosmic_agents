package server.agents.capabilities.partyquest.kpq;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.maps.Foothold;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentKpqStageOneThiefLandingPolicyTest {
    @Test
    void recognizesOnlyDownwardRopeProgress() {
        assertFalse(AgentKpqStageOneThiefLandingPolicy.descending(false, 0, 200, 0));
        assertFalse(AgentKpqStageOneThiefLandingPolicy.descending(true, 220, 210, -2));
        assertTrue(AgentKpqStageOneThiefLandingPolicy.descending(true, 210, 214, 0));
        assertTrue(AgentKpqStageOneThiefLandingPolicy.descending(false, 0, 200, 3));
    }

    @Test
    void selectsTheNearestPointInsideTheConfiguredEdgeMargin() {
        AgentNavigationGraph.Region platform = platform(100, 500, 300);

        assertEquals(new Point(180, 300),
                AgentKpqStageOneThiefLandingPolicy.nearestInteriorPoint(
                        new Point(110, 300), platform, 80));
        assertEquals(new Point(300, 300),
                AgentKpqStageOneThiefLandingPolicy.nearestInteriorPoint(
                        new Point(300, 300), platform, 80));
        assertEquals(new Point(420, 300),
                AgentKpqStageOneThiefLandingPolicy.nearestInteriorPoint(
                        new Point(490, 300), platform, 80));
    }

    @Test
    void scalesTheMarginForShortPlatforms() {
        AgentNavigationGraph.Region platform = platform(0, 90, 200);

        assertEquals(new Point(30, 200),
                AgentKpqStageOneThiefLandingPolicy.nearestInteriorPoint(
                        new Point(0, 200), platform, 80));
    }

    private static AgentNavigationGraph.Region platform(int left, int right, int y) {
        Foothold foothold = new Foothold(new Point(left, y), new Point(right, y), 1);
        return new AgentNavigationGraph.Region(
                1, List.of(new AgentNavigationGraph.Segment(foothold)));
    }
}
