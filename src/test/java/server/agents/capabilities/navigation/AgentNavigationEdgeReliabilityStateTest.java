package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationEdgeReliabilityStateTest {
    @Test
    void repeatedFailuresAreAgentMapAndEdgeScoped() {
        AgentNavigationEdgeReliabilityState firstAgent = new AgentNavigationEdgeReliabilityState();
        AgentNavigationEdgeReliabilityState secondAgent = new AgentNavigationEdgeReliabilityState();
        AgentNavigationGraph.Edge failed = edge(1, 2, 0);
        AgentNavigationGraph.Edge other = edge(1, 3, 20);

        firstAgent.recordFailure(100, failed, 1_000, 3, 30_000, 60_000, 32);
        firstAgent.recordFailure(100, failed, 1_100, 3, 30_000, 60_000, 32);
        assertFalse(firstAgent.isSuppressed(100, failed, 1_200, 60_000));
        firstAgent.recordFailure(100, failed, 1_200, 3, 30_000, 60_000, 32);

        assertTrue(firstAgent.isSuppressed(100, failed, 1_300, 60_000));
        assertFalse(firstAgent.isSuppressed(100, other, 1_300, 60_000));
        assertFalse(secondAgent.isSuppressed(100, failed, 1_300, 60_000));
        assertFalse(firstAgent.isSuppressed(101, failed, 1_300, 60_000),
                "changing maps resets this Agent's ledger");
    }

    @Test
    void suppressionExpiresAndSuccessClearsFailures() {
        AgentNavigationEdgeReliabilityState state = new AgentNavigationEdgeReliabilityState();
        AgentNavigationGraph.Edge edge = edge(1, 2, 0);
        failThree(state, edge);

        assertFalse(state.isSuppressed(100, edge, 31_201, 60_000));
        assertTrue(state.penalty(100, edge, 31_201, 60_000, 2_000, 10_000) > 0);
        state.recordSuccess(100, edge);
        assertEquals(0, state.penalty(100, edge, 31_202, 60_000, 2_000, 10_000));
        assertEquals(0, state.trackedEdgeCount(100, 31_202, 60_000));
    }

    @Test
    void failuresDuringSuppressionDoNotExtendItsFixedExpiry() {
        AgentNavigationEdgeReliabilityState state = new AgentNavigationEdgeReliabilityState();
        AgentNavigationGraph.Edge edge = edge(1, 2, 0);
        failThree(state, edge);
        state.recordFailure(100, edge, 20_000, 3, 30_000, 60_000, 32);

        assertFalse(state.isSuppressed(100, edge, 31_201, 60_000));
    }

    @Test
    void retainedStateAndPenaltyAreBounded() {
        AgentNavigationEdgeReliabilityState state = new AgentNavigationEdgeReliabilityState();
        for (int index = 0; index < 20; index++) {
            AgentNavigationGraph.Edge edge = edge(index, index + 1, index * 10);
            state.recordFailure(100, edge, 1_000 + index, 3, 30_000, 60_000, 8);
        }

        assertEquals(8, state.trackedEdgeCount(100, 2_000, 60_000));
        AgentNavigationGraph.Edge latest = edge(19, 20, 190);
        assertEquals(2_000, state.penalty(100, latest, 2_000, 60_000, 2_000, 10_000));
        for (int count = 0; count < 10; count++) {
            state.recordFailure(100, latest, 2_100 + count, 3, 30_000, 60_000, 8);
        }
        assertEquals(10_000, state.penalty(100, latest, 3_000, 60_000, 2_000, 10_000));
    }

    @Test
    void motionRefreshesAttemptButMotionlessAttemptTimesOut() {
        AgentNavigationEdgeReliabilityState state = new AgentNavigationEdgeReliabilityState();
        AgentNavigationGraph.Edge edge = edge(1, 2, 0);
        state.beginAttempt(100, edge, 1, new Point(0, 100), 1_000);

        assertNull(state.observeAttempt(100, 1, new Point(10, 100), 4_400, 3_500, 6));
        assertNull(state.observeAttempt(100, 1, new Point(10, 100), 7_899, 3_500, 6));
        assertNotNull(state.observeAttempt(100, 1, new Point(10, 100), 7_900, 3_500, 6));
    }

    @Test
    void reachingDestinationClearsFailureState() {
        AgentNavigationEdgeReliabilityState state = new AgentNavigationEdgeReliabilityState();
        AgentNavigationGraph.Edge edge = edge(1, 2, 0);
        state.recordFailure(100, edge, 900, 3, 30_000, 60_000, 32);
        state.beginAttempt(100, edge, 1, new Point(0, 100), 1_000);

        assertNull(state.observeAttempt(100, 2, new Point(100, 100), 1_500, 3_500, 6));
        assertEquals(0, state.penalty(100, edge, 1_500, 60_000, 2_000, 10_000));
    }

    @Test
    void syntheticCollapsedHandoffMatchesUnderlyingRiskyGraphEdge() {
        AgentNavigationEdgeReliabilityState state = new AgentNavigationEdgeReliabilityState();
        AgentNavigationGraph.Edge underlying = edge(2, 3, 100);
        AgentNavigationGraph.Edge collapsed = new AgentNavigationGraph.Edge(
                1, underlying.toRegionId, underlying.type,
                underlying.startPoint, underlying.endPoint,
                underlying.launchMinX, underlying.launchMaxX, underlying.launchStepX,
                underlying.portalId, underlying.ropeX, underlying.ropeTopY,
                underlying.ropeBottomY, underlying.cost + 10);

        state.recordFailure(100, collapsed, 1_000, 1, 30_000, 60_000, 32);

        assertTrue(state.isSuppressed(100, underlying, 1_100, 60_000));
        assertEquals(2_000,
                state.penalty(100, underlying, 1_100, 60_000, 2_000, 10_000));
    }

    private static void failThree(AgentNavigationEdgeReliabilityState state,
                                  AgentNavigationGraph.Edge edge) {
        state.recordFailure(100, edge, 1_000, 3, 30_000, 60_000, 32);
        state.recordFailure(100, edge, 1_100, 3, 30_000, 60_000, 32);
        state.recordFailure(100, edge, 1_200, 3, 30_000, 60_000, 32);
    }

    private static AgentNavigationGraph.Edge edge(int from, int to, int x) {
        return new AgentNavigationGraph.Edge(from, to, AgentNavigationGraph.EdgeType.JUMP,
                new Point(x, 100), new Point(x + 100, 100),
                x - 10, x + 10, 0, -1, 0, 0, 0, 100);
    }
}
