package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentNavigationEdgeExecutionStateServiceTest {
    @Test
    void setEdgeExecutionTargetStoresEndPointAsNonPreciseWaypoint() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotNavigationDebugStateRuntime.setNavPreciseTarget(entry, true);
        AgentNavigationGraph.Edge edge = new AgentNavigationGraph.Edge(
                1,
                2,
                AgentNavigationGraph.EdgeType.JUMP,
                new Point(10, 20),
                new Point(30, 40),
                10,
                20,
                1,
                0,
                0,
                0,
                0,
                100);

        AgentNavigationEdgeExecutionStateService.setEdgeExecutionTarget(entry, edge);

        assertEquals(new Point(30, 40), AgentBotNavigationDebugStateRuntime.navTargetPosition(entry));
        assertFalse(AgentBotNavigationDebugStateRuntime.navPreciseTarget(entry));
    }
}
