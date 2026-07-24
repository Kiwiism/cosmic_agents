package server.agents.capabilities.navigation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLithHarborArrivalRouteRuntimeTest {
    @Test
    void stagesShanksArrivalsOnlyOnTheUpperShipPlatform() {
        Character agent = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(agent.getMapId()).thenReturn(104_000_000);
        when(agent.getMap()).thenReturn(map);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.groundPoint(eq(map), any(Point.class)))
                .thenAnswer(invocation -> invocation.getArgument(1));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        try (var gatewayRuntime = mockStatic(AgentPrimitiveCapabilityGatewayRuntime.class)) {
            gatewayRuntime.when(AgentPrimitiveCapabilityGatewayRuntime::gateway)
                    .thenReturn(gateway);

            AgentLithHarborArrivalRouteRuntime.stageAfterShanks(entry, agent, 91);

            verify(gateway).stagePosition(eq(entry), eq(agent),
                    org.mockito.ArgumentMatchers.argThat(position ->
                            position.x >= 4_050 && position.x <= 4_325
                                    && position.y == -223));
            verify(gateway).prepareNavigation(entry, agent);
        }
    }

    @Test
    void selectsHiddenPortalBasedOnCurrentShipSection() {
        assertEquals(31, portalAt(new Point(4_188, -223)));
        assertEquals(20, portalAt(new Point(5_180, -319)));
        assertEquals(20, portalAt(new Point(4_300, 527)));
        assertEquals(30, portalAt(new Point(-572, 191)));
        assertNull(portalAt(new Point(2_407, -134)));
        assertNull(portalAt(new Point(2_894, 423)));
    }

    @Test
    void spreadsSequentialAgentsAcrossStableShipDescentLanes() {
        Set<Point> destinations = new HashSet<>();
        int sampleSize = AgentLithHarborArrivalRouteRuntime.descentLaneCount() * 4;
        for (int agentId = 1; agentId <= sampleSize; agentId++) {
            Point first = AgentLithHarborArrivalRouteRuntime.descentWaypoint(agentId);
            Point second = AgentLithHarborArrivalRouteRuntime.descentWaypoint(agentId);
            assertEquals(first, second, "an Agent must not switch descent lanes between ticks");
            destinations.add(first);
        }

        assertTrue(destinations.size() >= AgentLithHarborArrivalRouteRuntime.descentLaneCount(),
                "a cohort should spread across every authored descent lane");
        assertTrue(destinations.stream().allMatch(point ->
                        point.x >= 2_770 && point.x <= 4_790 && point.y > 0),
                "all descent destinations must be below the upper ship on supported surfaces");
    }

    @Test
    void routesAnUpperShipAgentToItsOwnDescentLaneBeforeUsingAHiddenPortal() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(37);
        when(agent.getMapId()).thenReturn(104_000_000);
        when(agent.getPosition()).thenReturn(new Point(4_188, -223));
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.grounded(agent)).thenReturn(true);
        when(gateway.stuckDurationMs(any())).thenReturn(0);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        assertEquals(AgentLithHarborArrivalRouteRuntime.TravelProgress.YIELD_TO_MOVEMENT,
                AgentLithHarborArrivalRouteRuntime.advanceToTown(entry, agent, gateway));

        verify(gateway).navigate(eq(entry),
                eq(AgentLithHarborArrivalRouteRuntime.descentWaypoint(37)), eq(true));
        verify(gateway, never()).portalPosition(any(), any(Integer.class));
    }

    private static Integer portalAt(Point position) {
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(104_000_000);
        when(agent.getPosition()).thenReturn(position);
        return AgentLithHarborArrivalRouteRuntime.nextPortalId(agent);
    }
}
