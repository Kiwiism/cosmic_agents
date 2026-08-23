package server.agents.capabilities.navigation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentClimbStateRuntime;
import server.agents.capabilities.movement.AgentRopeMovementService;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentNavigationClimbExitExecutionServiceTest {
    @Test
    void executesGraphAuthoredZeroStepTopExit() {
        Rope rope = new Rope(300, 100, 240, false);
        AgentNavigationGraph.Region ropeRegion = new AgentNavigationGraph.Region(1, 300, 100, 240, false);
        AgentNavigationGraph.Region groundRegion = new AgentNavigationGraph.Region(2, List.of(
                new AgentNavigationGraph.Segment(new Foothold(
                        new Point(300, 98), new Point(360, 98), 2))));
        AgentNavigationGraph graph = new AgentNavigationGraph(
                100, 1, List.of(ropeRegion, groundRegion),
                Map.of(1, ropeRegion, 2, groundRegion), Map.of(), Map.of(), Set.of());
        AgentNavigationGraph.Edge exit = new AgentNavigationGraph.Edge(
                1, 2, AgentNavigationGraph.EdgeType.CLIMB,
                new Point(300, 100), new Point(300, 98),
                0, 0, 0, 0, 300, 100, 240, 50);

        MapleMap map = mock(MapleMap.class);
        when(map.getRopes()).thenReturn(List.of(rope));
        when(map.isObservedByPlayer()).thenReturn(false);
        Character agent = mock(Character.class);
        when(agent.getMap()).thenReturn(map);
        when(agent.getMapId()).thenReturn(100);
        when(agent.getPosition()).thenReturn(new Point(300, 102));
        when(agent.getHp()).thenReturn(1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentRopeMovementService.attachToRope(entry, agent, rope, 102, -1);

        assertTrue(AgentNavigationClimbExitExecutionService.tryExecuteClimbExit(
                graph, entry, agent, new Point(300, 102), exit));

        verify(agent).setPosition(new Point(300, 98));
        assertFalse(AgentClimbStateRuntime.climbing(entry));
    }
}
