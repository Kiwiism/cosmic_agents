package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationPathService;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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

    @Test
    void strictCombatRouteRejectsStructurallyInvalidFirstEdge() {
        AgentNavigationGraph.Edge invalid = new AgentNavigationGraph.Edge(
                1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(150, 100), new Point(200, 100),
                140, 160, 0, -1, 0, 0, 0, 100);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);

        assertFalse(AgentCombatRouteService.firstEdgeExecutable(
                graph(invalid), entry, agent,
                1, new Point(0, 100), complete(invalid), 1_000));
        assertEquals(UNREACHABLE, AgentCombatRouteService.pathCost(
                graph(invalid), null, new Point(0, 100), 1,
                new Point(200, 100), 2, AgentMovementProfile.base(),
                entry, agent, UNREACHABLE));
    }

    @Test
    void strictCombatRouteAllowsApproachToValidFirstEdge() {
        AgentNavigationGraph.Edge valid = new AgentNavigationGraph.Edge(
                1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(100, 100), new Point(200, 100),
                90, 100, 0, -1, 0, 0, 0, 100);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);

        assertTrue(AgentCombatRouteService.firstEdgeExecutable(
                graph(valid), entry, agent,
                1, new Point(0, 100), complete(valid), 1_000));
        assertTrue(AgentCombatRouteService.pathCost(
                graph(valid), null, new Point(0, 100), 1,
                new Point(200, 100), 2, AgentMovementProfile.base(),
                entry, agent, UNREACHABLE) < UNREACHABLE);
    }

    private static AgentNavigationPathService.SearchOutcome complete(
            AgentNavigationGraph.Edge edge) {
        return new AgentNavigationPathService.SearchOutcome(
                List.of(edge), 750, 2, false,
                true, false, false, 2, 10);
    }

    private static AgentNavigationGraph graph(AgentNavigationGraph.Edge edge) {
        AgentNavigationGraph.Region from = region(1, 0, 100);
        AgentNavigationGraph.Region to = region(2, 200, 300);
        return new AgentNavigationGraph(
                100, 1, AgentMovementProfile.base(),
                List.of(from, to), Map.of(1, from, 2, to),
                Map.of(1, 1, 2, 2), Map.of(1, List.of(edge)), Set.of());
    }

    private static AgentNavigationGraph.Region region(int id, int minX, int maxX) {
        return new AgentNavigationGraph.Region(id, List.of(new AgentNavigationGraph.Segment(
                new Foothold(new Point(minX, 100), new Point(maxX, 100), id))));
    }

    private static AgentNavigationGraph.Edge edge() {
        return new AgentNavigationGraph.Edge(1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(0, 100), new Point(100, 100), 0, 0, 0, 0, 0, 100);
    }
}
