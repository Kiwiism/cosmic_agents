package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationPreciseTargetServiceTest {
    @Test
    void airborneNeverUsesPreciseTarget() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotMovementStateRuntime.setInAir(entry, true);

        assertFalse(AgentNavigationPreciseTargetService.shouldUsePreciseTarget(
                null, entry, new Point(0, 0), edge(AgentNavigationGraph.EdgeType.WALK, 0), readiness(false)));
    }

    @Test
    void walkUsesPreciseOnlyForMovementConsumingWalkEdges() {
        BotEntry entry = new BotEntry(null, null, null);

        assertTrue(AgentNavigationPreciseTargetService.shouldUsePreciseTarget(
                null, entry, new Point(0, 0),
                new AgentNavigationGraph.Edge(1, 2, AgentNavigationGraph.EdgeType.WALK,
                        new Point(0, 0), new Point(10, 0), 0, 0, 0, 0, 0, 0, 0, 100),
                readiness(false)));
        assertFalse(AgentNavigationPreciseTargetService.shouldUsePreciseTarget(
                null, entry, new Point(0, 0),
                new AgentNavigationGraph.Edge(1, 2, AgentNavigationGraph.EdgeType.WALK,
                        new Point(0, 0), new Point(2, 0), 0, 0, 0, 0, 0, 0, 0, 100),
                readiness(false)));
    }

    @Test
    void jumpDropAndClimbUsePreciseUntilTheirExecutionPredicateIsReady() {
        BotEntry entry = new BotEntry(null, null, null);

        assertTrue(AgentNavigationPreciseTargetService.shouldUsePreciseTarget(
                null, entry, new Point(0, 0), edge(AgentNavigationGraph.EdgeType.JUMP, 0), readiness(false)));
        assertFalse(AgentNavigationPreciseTargetService.shouldUsePreciseTarget(
                null, entry, new Point(0, 0), edge(AgentNavigationGraph.EdgeType.JUMP, 0), readiness(true)));

        assertTrue(AgentNavigationPreciseTargetService.shouldUsePreciseTarget(
                null, entry, new Point(0, 0), edge(AgentNavigationGraph.EdgeType.DROP, 0), readiness(false)));
        assertFalse(AgentNavigationPreciseTargetService.shouldUsePreciseTarget(
                null, entry, new Point(0, 0), edge(AgentNavigationGraph.EdgeType.DROP, 0), readiness(true)));
        assertFalse(AgentNavigationPreciseTargetService.shouldUsePreciseTarget(
                null, entry, new Point(0, 0), edge(AgentNavigationGraph.EdgeType.DROP, 1), readiness(false)));

        assertTrue(AgentNavigationPreciseTargetService.shouldUsePreciseTarget(
                null, entry, new Point(0, 0), edge(AgentNavigationGraph.EdgeType.CLIMB, 0), readiness(false)));
        assertFalse(AgentNavigationPreciseTargetService.shouldUsePreciseTarget(
                null, entry, new Point(0, 0), edge(AgentNavigationGraph.EdgeType.CLIMB, 0), readiness(true)));
    }

    @Test
    void climbingJumpExitUsesPreciseUntilExitReady() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotClimbStateRuntime.setClimbingOnRope(entry, new server.maps.Rope(100, 0, 100, false));

        assertTrue(AgentNavigationPreciseTargetService.shouldUsePreciseTarget(
                null, entry, new Point(0, 0), edge(AgentNavigationGraph.EdgeType.CLIMB, 1), readiness(false)));
        assertFalse(AgentNavigationPreciseTargetService.shouldUsePreciseTarget(
                null, entry, new Point(0, 0), edge(AgentNavigationGraph.EdgeType.CLIMB, 1), readiness(true)));
        assertFalse(AgentNavigationPreciseTargetService.shouldUsePreciseTarget(
                null, entry, new Point(0, 0), edge(AgentNavigationGraph.EdgeType.CLIMB, 0), readiness(false)));
    }

    @Test
    void portalUsesPreciseUntilAtEdgeStart() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentNavigationGraph.Edge portal = edge(AgentNavigationGraph.EdgeType.PORTAL, 0);

        assertFalse(AgentNavigationPreciseTargetService.shouldUsePreciseTarget(
                null, entry, new Point(0, 0), portal, readiness(false)));
        assertTrue(AgentNavigationPreciseTargetService.shouldUsePreciseTarget(
                null, entry, new Point(30, 0), portal, readiness(false)));
    }

    private static AgentNavigationGraph.Edge edge(AgentNavigationGraph.EdgeType type, int launchStepX) {
        return new AgentNavigationGraph.Edge(1, 2, type,
                new Point(0, 0), new Point(100, 0), -10, 10, launchStepX, 0, 0, 0, 0, 100);
    }

    private static AgentNavigationPreciseTargetService.EdgeReadiness readiness(boolean ready) {
        return new AgentNavigationPreciseTargetService.EdgeReadiness() {
            @Override
            public boolean canExecuteSelectedJump(AgentNavigationGraph graph, BotEntry entry, Point botPos,
                                                  AgentNavigationGraph.Edge edge) {
                return ready;
            }

            @Override
            public boolean canExecuteDrop(AgentNavigationGraph graph, BotEntry entry, Point botPos,
                                          AgentNavigationGraph.Edge edge) {
                return ready;
            }

            @Override
            public boolean canExecuteClimbExit(AgentNavigationGraph graph, BotEntry entry, Point botPos,
                                               AgentNavigationGraph.Edge edge) {
                return ready;
            }

            @Override
            public boolean canExecuteClimbEntry(AgentNavigationGraph graph, BotEntry entry, Point botPos,
                                                AgentNavigationGraph.Edge edge) {
                return ready;
            }
        };
    }
}
