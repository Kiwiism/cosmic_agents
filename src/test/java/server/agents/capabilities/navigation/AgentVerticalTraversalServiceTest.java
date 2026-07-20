package server.agents.capabilities.navigation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentClimbStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;
import server.maps.Rope;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentVerticalTraversalServiceTest {
    @Test
    void commitsEntryAndExitAcrossLiveTargetChangesUntilGroundedHandoff() {
        Fixture fixture = fixture();
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character bot = mock(Character.class);
        Point originalTarget = new Point(180, 100);

        assertTrue(AgentVerticalTraversalService.beginIfRopeEntry(
                fixture.graph, entry, bot, fixture.entryEdge, 3, originalTarget,
                (graph, candidate, startPosition, startRegionId, targetRegionId, targetPosition) -> fixture.exitEdge));

        AgentVerticalTraversalService.TraversalDirective approach = resolve(fixture, entry, bot, 1, true);
        assertSame(fixture.entryEdge, approach.edge());
        assertEquals(originalTarget, approach.targetPosition());
        assertEquals(-1, AgentVerticalTraversalService.committedClimbDirection(entry, 300));

        AgentClimbStateRuntime.setClimbingOnRope(entry, mock(Rope.class));
        AgentVerticalTraversalService.TraversalDirective climbing = resolve(fixture, entry, bot, 2, true);
        assertSame(fixture.exitEdge, climbing.edge());
        assertEquals(originalTarget, climbing.targetPosition(),
                "a new combat target must not replace the destination during the crossing");

        AgentClimbStateRuntime.setClimbingOnRope(entry, null);
        AgentMovementStateRuntime.setInAir(entry, true);
        assertSame(fixture.exitEdge, resolve(fixture, entry, bot, 3, false).edge());

        AgentMovementStateRuntime.setInAir(entry, false);
        AgentVerticalTraversalService.TraversalDirective grounded = resolve(fixture, entry, bot, 3, false);
        assertTrue(grounded.holdGroundedExit());
        assertTrue(resolve(fixture, entry, bot, 3, false).holdGroundedExit());
        assertNull(resolve(fixture, entry, bot, 3, true));
        assertFalse(AgentVerticalTraversalStateRuntime.active(entry));
    }

    @Test
    void navigationResetCancelsTraversalTransaction() {
        Fixture fixture = fixture();
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertTrue(AgentVerticalTraversalService.beginIfRopeEntry(
                fixture.graph, entry, mock(Character.class), fixture.entryEdge, 3, new Point(180, 100),
                (graph, candidate, startPosition, startRegionId, targetRegionId, targetPosition) -> fixture.exitEdge));

        AgentMovementStateResetService.clearNavigationState(entry);

        assertFalse(AgentVerticalTraversalStateRuntime.active(entry));
    }

    private static AgentVerticalTraversalService.TraversalDirective resolve(Fixture fixture,
                                                                             AgentRuntimeEntry entry,
                                                                             Character bot,
                                                                             int currentRegionId,
                                                                             boolean runAiTick) {
        return AgentVerticalTraversalService.resolve(
                fixture.graph, entry, bot, currentRegionId, runAiTick,
                (graph, candidate, edge) -> true);
    }

    private static Fixture fixture() {
        AgentNavigationGraph.Region lower = new AgentNavigationGraph.Region(1, List.of(
                new AgentNavigationGraph.Segment(new Foothold(new Point(0, 300), new Point(200, 300), 1))));
        AgentNavigationGraph.Region rope = new AgentNavigationGraph.Region(2, 100, 100, 300, false);
        AgentNavigationGraph.Region upper = new AgentNavigationGraph.Region(3, List.of(
                new AgentNavigationGraph.Segment(new Foothold(new Point(0, 100), new Point(200, 100), 2))));
        AgentNavigationGraph.Edge entryEdge = edge(1, 2, new Point(100, 300), new Point(100, 300));
        AgentNavigationGraph.Edge exitEdge = edge(2, 3, new Point(100, 100), new Point(120, 100));
        AgentNavigationGraph graph = new AgentNavigationGraph(
                910000300, 1,
                List.of(lower, rope, upper),
                Map.of(1, lower, 2, rope, 3, upper),
                Map.of(),
                Map.of(1, List.of(entryEdge), 2, List.of(exitEdge)),
                Set.of());
        return new Fixture(graph, entryEdge, exitEdge);
    }

    private static AgentNavigationGraph.Edge edge(int fromRegionId,
                                                  int toRegionId,
                                                  Point start,
                                                  Point end) {
        return new AgentNavigationGraph.Edge(
                fromRegionId, toRegionId, AgentNavigationGraph.EdgeType.CLIMB,
                start, end, start.x, start.x, 0, 0, 100, 100, 300, 100);
    }

    private record Fixture(AgentNavigationGraph graph,
                           AgentNavigationGraph.Edge entryEdge,
                           AgentNavigationGraph.Edge exitEdge) {
    }
}
