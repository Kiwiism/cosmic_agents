package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationCommittedEdgeServiceTest {
    @Test
    void sameEdgeMatchesAllNavigationFields() {
        AgentNavigationGraph.Edge left = edge(1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(10, 20), new Point(40, 60), 3, 12, -7, 0, 0, 0, 0);
        AgentNavigationGraph.Edge same = edge(1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(10, 20), new Point(40, 60), 3, 12, -7, 0, 0, 0, 0);
        AgentNavigationGraph.Edge differentLaunch = edge(1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(10, 20), new Point(40, 60), 4, 12, -7, 0, 0, 0, 0);

        assertTrue(AgentNavigationCommittedEdgeService.sameEdge(left, same));
        assertFalse(AgentNavigationCommittedEdgeService.sameEdge(left, differentLaunch));
        assertFalse(AgentNavigationCommittedEdgeService.sameEdge(left, null));
    }

    @Test
    void retainCommittedGroundEdgeOnlyForNonWalkSameRegionReplacement() {
        AgentNavigationGraph.Edge committedDrop = edge(80, 83, AgentNavigationGraph.EdgeType.DROP,
                new Point(7, -34), new Point(-84, 99), 7, 7, 0, 0, 0, 0, 0);
        AgentNavigationGraph.Edge replacementJump = edge(80, 83, AgentNavigationGraph.EdgeType.JUMP,
                new Point(5, -34), new Point(-99, 95), -35, 45, -7, 0, 0, 0, 0);
        AgentNavigationGraph.Edge replacementOtherRegion = edge(80, 84, AgentNavigationGraph.EdgeType.JUMP,
                new Point(5, -34), new Point(-99, 95), -35, 45, -7, 0, 0, 0, 0);
        AgentNavigationGraph.Edge replacementWalk = edge(80, 83, AgentNavigationGraph.EdgeType.WALK,
                new Point(5, -34), new Point(-99, 95), 0, 0, 0, 0, 0, 0, 0);

        assertTrue(AgentNavigationCommittedEdgeService.shouldRetainCommittedGroundEdge(committedDrop, replacementJump));
        assertFalse(AgentNavigationCommittedEdgeService.shouldRetainCommittedGroundEdge(committedDrop, replacementOtherRegion));
        assertFalse(AgentNavigationCommittedEdgeService.shouldRetainCommittedGroundEdge(committedDrop, replacementWalk));
    }

    private static AgentNavigationGraph.Edge edge(int fromRegionId,
                                                  int toRegionId,
                                                  AgentNavigationGraph.EdgeType type,
                                                  Point start,
                                                  Point end,
                                                  int launchMinX,
                                                  int launchMaxX,
                                                  int launchStepX,
                                                  int portalId,
                                                  int ropeX,
                                                  int ropeTopY,
                                                  int ropeBottomY) {
        return new AgentNavigationGraph.Edge(fromRegionId, toRegionId, type,
                start, end, launchMinX, launchMaxX, launchStepX, portalId, ropeX, ropeTopY, ropeBottomY, 100);
    }
}
