package server.agents.capabilities.navigation;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;
import server.maps.Portal;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLithHarborArrivalRouteRuntimeTest {
    @Test
    void fallsBackToTheAuthoredMapleIslandShipArrivalPortal() {
        MapleMap map = mock(MapleMap.class);
        Portal shipArrival = mock(Portal.class);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.groundPoint(eq(map), any(Point.class))).thenReturn(null);
        when(map.getPortal("maple00")).thenReturn(shipArrival);
        when(shipArrival.getPosition()).thenReturn(new Point(84, 12));

        try (var gatewayRuntime = mockStatic(AgentPrimitiveCapabilityGatewayRuntime.class)) {
            gatewayRuntime.when(AgentPrimitiveCapabilityGatewayRuntime::gateway)
                    .thenReturn(gateway);

            assertEquals(new Point(84, 12),
                    AgentLithHarborArrivalRouteRuntime.victoriaArrivalPosition(map, 91));
        }
    }

    @Test
    void selectsHiddenPortalBasedOnCurrentShipSection() {
        assertEquals(30, portalAt(new Point(84, 27)));
        assertEquals(31, portalAt(new Point(4_188, -223)));
        assertEquals(20, portalAt(new Point(5_180, -319)));
        assertEquals(20, portalAt(new Point(4_300, 527)));
        assertEquals(30, portalAt(new Point(-572, 191)));
        assertNull(portalAt(new Point(2_407, -134)));
        assertNull(portalAt(new Point(2_894, 423)));
    }

    @Test
    void routesAnUpperShipAgentDirectlyTowardItsNearestAuthoredExit() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(37);
        when(agent.getMapId()).thenReturn(104_000_000);
        when(agent.getPosition()).thenReturn(new Point(4_188, -223));
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.grounded(agent)).thenReturn(true);
        when(gateway.portalPosition(agent, 31)).thenReturn(new Point(4_000, -223));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        assertEquals(AgentLithHarborArrivalRouteRuntime.TravelProgress.YIELD_TO_MOVEMENT,
                AgentLithHarborArrivalRouteRuntime.advanceToTown(entry, agent, gateway));

        verify(gateway).navigate(entry, new Point(4_000, -223), true);
    }

    @Test
    void routesTheMapleIslandShipDeckToItsLowerLeftExit() {
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(104_000_000);
        when(agent.getPosition()).thenReturn(new Point(84, 27));
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.grounded(agent)).thenReturn(true);
        when(gateway.portalPosition(agent, 30)).thenReturn(new Point(-572, 191));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        assertEquals(AgentLithHarborArrivalRouteRuntime.TravelProgress.YIELD_TO_MOVEMENT,
                AgentLithHarborArrivalRouteRuntime.advanceToTown(entry, agent, gateway));

        verify(gateway).navigate(entry, new Point(-572, 191), true);
    }

    @Test
    void recognizesOnlyAnExistingSyntheticArrivalDeckPlacement() {
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(104_000_000);
        when(agent.getPosition()).thenReturn(new Point(84, 27));
        assertTrue(AgentLithHarborArrivalRouteRuntime.isVictoriaArrivalPosition(agent));

        when(agent.getPosition()).thenReturn(new Point(1_400, 27));
        assertFalse(AgentLithHarborArrivalRouteRuntime.isVictoriaArrivalPosition(agent));
    }

    @Test
    void characterNameSelectionIsCaseInsensitive() {
        MapleMap map = mock(MapleMap.class);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.groundPoint(eq(map), any(Point.class))).thenAnswer(invocation -> invocation.getArgument(1));

        try (var gatewayRuntime = mockStatic(AgentPrimitiveCapabilityGatewayRuntime.class)) {
            gatewayRuntime.when(AgentPrimitiveCapabilityGatewayRuntime::gateway)
                    .thenReturn(gateway);

            assertEquals(
                    AgentLithHarborArrivalRouteRuntime.victoriaArrivalPosition(map, "kiwiagent"),
                    AgentLithHarborArrivalRouteRuntime.victoriaArrivalPosition(map, "KiwiAgent"));
        }
    }

    private static Integer portalAt(Point position) {
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(104_000_000);
        when(agent.getPosition()).thenReturn(position);
        return AgentLithHarborArrivalRouteRuntime.nextPortalId(agent);
    }
}
