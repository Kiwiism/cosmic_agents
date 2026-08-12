package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.combat.AgentGrindTargetStateRuntime;
import server.agents.capabilities.combat.AgentCombatObjectiveTargetStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.awt.Point;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMovementStateResetServiceTest {
    @Test
    void clearingFailedNavigationStepPreservesCombatTargetAndRequestedDestination() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Monster target = mock(Monster.class);
        when(target.getId()).thenReturn(100);
        Point destination = new Point(500, 100);
        AgentNavigationGraph.Edge edge = new AgentNavigationGraph.Edge(
                1, 2, AgentNavigationGraph.EdgeType.JUMP,
                new Point(0, 100), new Point(100, 100), 0, 0, 0, 0, 0, 100);
        AgentGrindTargetStateRuntime.setTarget(entry, target);
        AgentCombatObjectiveTargetStateRuntime.setTargetPreferences(
                entry, Set.of(100), Set.of(200));
        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, edge);
        AgentNavigationDebugStateRuntime.setPlannedNavigationTargetPosition(entry, destination);

        AgentMovementStateResetService.clearNavigationStep(entry);

        assertSame(target, AgentGrindTargetStateRuntime.target(entry));
        assertTrue(AgentCombatObjectiveTargetStateRuntime.prefers(entry, 100));
        assertTrue(AgentCombatObjectiveTargetStateRuntime.allows(entry, 200));
        assertNull(AgentNavigationDebugStateRuntime.activeNavigationEdge(entry));
    }
}
