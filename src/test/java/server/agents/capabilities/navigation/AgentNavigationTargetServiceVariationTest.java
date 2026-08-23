package server.agents.capabilities.navigation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentNavigationTargetServiceVariationTest {
    @Test
    void unresolvedOrCrossRegionFallbackHoldsPositionInsteadOfSteeringThroughGeometry() {
        Point position = new Point(-495, -62);
        Point olaf = new Point(3392, 518);

        assertEquals(position,
                AgentNavigationTargetService.safeFallbackTarget(position, olaf, -1, 72));
        assertEquals(position,
                AgentNavigationTargetService.safeFallbackTarget(position, olaf, 23, 72));
        assertEquals(olaf,
                AgentNavigationTargetService.safeFallbackTarget(position, olaf, 72, 72));
    }

    @Test
    void climbingFallbackRetainsCallerDestinationAcrossTransientNoEdgeReplan() {
        Point ropePosition = new Point(-980, 1665);
        Point portal = new Point(-1002, 1453);

        assertEquals(portal,
                AgentNavigationTargetService.safeFallbackTarget(
                        ropePosition, portal, 33, 8, true));
        assertEquals(portal,
                AgentNavigationTargetService.safeFallbackTarget(
                        ropePosition, portal, 33, -1, true));
    }

    @Test
    void variationOnlyAppliesToTheActiveScriptedMoveTarget() {
        Character bot = mock(Character.class);
        when(bot.getId()).thenReturn(51);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMapleIslandTravelRuntime.configure(entry, new AgentMapleIslandTravelSettings(
                77L, true, 1.2d, false, 0.0d, 1_000L, 0L));
        Point scriptedTarget = new Point(300, 100);

        assertNull(AgentNavigationTargetService.scriptedRouteVariation(
                entry, 1010000, 4, scriptedTarget));

        AgentMoveTargetStateRuntime.setMoveTarget(entry, scriptedTarget, false);

        assertNotNull(AgentNavigationTargetService.scriptedRouteVariation(
                entry, 1010000, 4, scriptedTarget));
        assertNull(AgentNavigationTargetService.scriptedRouteVariation(
                entry, 1010000, 4, new Point(500, 100)));
    }

    @Test
    void rejectsEdgeThatLeavesAlreadyResolvedTargetRegion() {
        AgentNavigationGraph.Edge leaving = new AgentNavigationGraph.Edge(
                86, 83, AgentNavigationGraph.EdgeType.JUMP,
                new Point(253, 292), new Point(297, 223),
                0, 0, 0, 0, 0, 100);

        assertTrue(AgentNavigationTargetService.leavesResolvedTargetRegion(leaving, 86, 86));
        assertFalse(AgentNavigationTargetService.leavesResolvedTargetRegion(leaving, 86, 83));
    }

}
