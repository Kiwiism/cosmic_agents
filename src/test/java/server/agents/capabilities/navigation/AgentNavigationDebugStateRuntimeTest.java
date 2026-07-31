package server.agents.capabilities.navigation;

import server.agents.runtime.AgentRuntimeEntry;

import server.agents.capabilities.navigation.AgentNavigationGraph;

import client.Character;
import org.junit.jupiter.api.Test;
import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationDebugStateRuntimeTest {
    @Test
    void adaptsLastDecisionAndBlockReasonState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentNavigationDebugStateRuntime.setLastDecision(entry, "graph-warmup");
        assertTrue("graph-warmup".equals(AgentNavigationDebugStateRuntime.lastDecision(entry)));

        AgentNavigationDebugStateRuntime.setLastEdgeBlockReason(entry, "climb-pos");
        assertTrue("climb-pos".equals(AgentNavigationDebugStateRuntime.lastEdgeBlockReason(entry)));
        assertTrue("graph-warmup[climb-pos]".equals(
                AgentNavigationDebugStateRuntime.decisionWithBlockReason(entry)));

        AgentNavigationDebugStateRuntime.clearLastEdgeBlockReason(entry);
        assertTrue("graph-warmup".equals(AgentNavigationDebugStateRuntime.decisionWithBlockReason(entry)));
    }

    @Test
    void adaptsGraphWarmupFallbackState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentNavigationDebugStateRuntime.graphWarmupFallback(entry));

        AgentNavigationDebugStateRuntime.setGraphWarmupFallback(entry, true);

        assertTrue(AgentNavigationDebugStateRuntime.graphWarmupFallback(entry));

        AgentNavigationDebugStateRuntime.clearGraphWarmupFallback(entry);

        assertFalse(AgentNavigationDebugStateRuntime.graphWarmupFallback(entry));
    }

    @Test
    void adaptsNavTargetStateWithPointCopies() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Point waypoint = new Point(20, 30);

        AgentNavigationDebugStateRuntime.setNavTargetRegionId(entry, 7);
        AgentNavigationDebugStateRuntime.setNavWaypoint(entry, waypoint, true);

        waypoint.x = 99;
        Point exposed = AgentNavigationDebugStateRuntime.navTargetPosition(entry);
        exposed.y = 88;

        assertTrue(AgentNavigationDebugStateRuntime.hasNavTargetPosition(entry));
        assertEquals(new Point(20, 30), AgentNavigationDebugStateRuntime.navTargetPosition(entry));
        assertEquals(7, AgentNavigationDebugStateRuntime.navTargetRegionId(entry));
        assertTrue(AgentNavigationDebugStateRuntime.navPreciseTarget(entry));

        AgentNavigationDebugStateRuntime.clearNavTarget(entry);

        assertFalse(AgentNavigationDebugStateRuntime.hasNavTargetPosition(entry));
        assertNull(AgentNavigationDebugStateRuntime.navTargetPosition(entry));
        assertEquals(-1, AgentNavigationDebugStateRuntime.navTargetRegionId(entry));
        assertFalse(AgentNavigationDebugStateRuntime.navPreciseTarget(entry));
    }

    @Test
    void adaptsPortalUseCooldownState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentNavigationDebugStateRuntime.portalUseOnCooldown(entry, 1_000L));

        AgentNavigationDebugStateRuntime.setPortalUseCooldownUntilMs(entry, 1_500L);

        assertEquals(1_500L, AgentNavigationDebugStateRuntime.portalUseCooldownUntilMs(entry));
        assertTrue(AgentNavigationDebugStateRuntime.portalUseOnCooldown(entry, 1_000L));
        assertFalse(AgentNavigationDebugStateRuntime.portalUseOnCooldown(entry, 1_500L));
    }

    @Test
    void adaptsNavJumpLaunchCacheState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentNavigationGraph.Edge edge = new AgentNavigationGraph.Edge(
                1,
                2,
                AgentNavigationGraph.EdgeType.JUMP,
                new Point(10, 20),
                new Point(30, 40),
                0,
                0,
                5,
                15,
                0,
                100);

        AgentNavigationDebugStateRuntime.rememberNavJumpLaunch(entry, edge, 12);

        assertTrue(AgentNavigationDebugStateRuntime.hasNavJumpLaunchEdge(entry));
        assertTrue(AgentNavigationDebugStateRuntime.matchesNavJumpLaunchEdge(entry, edge));
        assertEquals(12, AgentNavigationDebugStateRuntime.navJumpLaunchX(entry));

        AgentNavigationDebugStateRuntime.clearNavJumpLaunch(entry);

        assertFalse(AgentNavigationDebugStateRuntime.hasNavJumpLaunchEdge(entry));
        assertFalse(AgentNavigationDebugStateRuntime.matchesNavJumpLaunchEdge(entry, edge));
        assertEquals(Integer.MIN_VALUE, AgentNavigationDebugStateRuntime.navJumpLaunchX(entry));
    }

    @Test
    void adaptsActiveNavigationEdgePresence() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentNavigationGraph.Edge edge = new AgentNavigationGraph.Edge(
                1,
                2,
                AgentNavigationGraph.EdgeType.WALK,
                new Point(10, 20),
                new Point(30, 20),
                0,
                0,
                0,
                0,
                0,
                20);

        assertFalse(AgentNavigationDebugStateRuntime.hasActiveNavigationEdge(entry));
        assertNull(AgentNavigationDebugStateRuntime.activeNavigationEdge(entry));

        AgentNavigationDebugStateRuntime.setActiveNavigationEdge(entry, edge);

        assertTrue(AgentNavigationDebugStateRuntime.hasActiveNavigationEdge(entry));
        assertEquals(edge, AgentNavigationDebugStateRuntime.activeNavigationEdge(entry));

        AgentNavigationDebugStateRuntime.clearActiveNavigationEdge(entry);

        assertFalse(AgentNavigationDebugStateRuntime.hasActiveNavigationEdge(entry));
        assertNull(AgentNavigationDebugStateRuntime.activeNavigationEdge(entry));
    }

}
