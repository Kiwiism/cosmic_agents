package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationPathService;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentCombatRouteCompletenessTest {
    private static final long UNREACHABLE = Long.MAX_VALUE / 4;

    @Test
    void combatRejectsPartialClosestFrontierAndUnreachableOutcomes() {
        AgentNavigationGraph.Edge frontier = edge();
        AgentNavigationPathService.SearchOutcome partial = new AgentNavigationPathService.SearchOutcome(
                List.of(frontier), Integer.MAX_VALUE, 2, false,
                false, true, true, 2, 10);
        AgentNavigationPathService.SearchOutcome unreachable = new AgentNavigationPathService.SearchOutcome(
                List.of(), Integer.MAX_VALUE, 2, false,
                false, false, false, 1, 10);

        assertEquals(UNREACHABLE, AgentCombatTargetRuntime.combatRouteCost(partial, UNREACHABLE));
        assertEquals(UNREACHABLE, AgentCombatTargetRuntime.combatRouteCost(unreachable, UNREACHABLE));
    }

    @Test
    void combatAcceptsOnlyCompleteRemoteOutcome() {
        AgentNavigationPathService.SearchOutcome complete = new AgentNavigationPathService.SearchOutcome(
                List.of(edge()), 750, 2, false,
                true, false, false, 2, 10);

        assertEquals(750, AgentCombatTargetRuntime.combatRouteCost(complete, UNREACHABLE));
    }

    private static AgentNavigationGraph.Edge edge() {
        return new AgentNavigationGraph.Edge(1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(0, 100), new Point(100, 100), 0, 0, 0, 0, 0, 100);
    }
}
