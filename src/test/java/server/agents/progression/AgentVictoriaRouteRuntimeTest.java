package server.agents.progression;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentVictoriaRouteRuntimeTest {
    @Test
    void authoredElliniaLibraryEntranceWinsOverAnInferredDirectPortal() {
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(101000000);
        when(agent.getPosition()).thenReturn(new Point(334, -3987));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.directPortalIdTo(agent, 101000003)).thenReturn(99);
        when(gateway.portalPosition(agent, 26)).thenReturn(new Point(334, -3987));
        when(gateway.enterPortal(agent, 26)).thenReturn(true);

        AgentVictoriaRouteRuntime.TravelOutcome outcome = AgentVictoriaRouteRuntime.travelStatus(
                entry, agent, 101000003, gateway, 1_000L);

        assertEquals(AgentVictoriaRouteRuntime.Status.MOVING, outcome.status());
        verify(gateway).enterPortal(agent, 26);
        verify(gateway, never()).enterPortal(agent, 99);
        verify(gateway, never()).directPortalIdTo(agent, 101000003);
    }

    @Test
    void newlyEnteredMapRemainsStoppedUntilItsArrivalPauseExpires() {
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(101000003);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        entry.capabilityStates().require(AgentVictoriaRouteState.STATE_KEY)
                .recordPortalSuccess(101000003, 1_000L, 1_500L);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);

        AgentVictoriaRouteRuntime.TravelOutcome settling = AgentVictoriaRouteRuntime.travelStatus(
                entry, agent, 101000003, gateway, 2_000L);
        AgentVictoriaRouteRuntime.TravelOutcome arrived = AgentVictoriaRouteRuntime.travelStatus(
                entry, agent, 101000003, gateway, 2_500L);

        assertEquals(AgentVictoriaRouteRuntime.Status.MOVING, settling.status());
        assertEquals(AgentVictoriaRouteRuntime.Status.ARRIVED, arrived.status());
        verify(gateway).stop(entry);
    }

    @Test
    void unobservedScriptedLibraryArrivalDoesNotConsumeTheNpcRouteImmediately() {
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(101000000, 101000003, 101000003);
        when(agent.getPosition()).thenReturn(new Point(334, -3987));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.portalPosition(agent, 26)).thenReturn(new Point(334, -3987));
        when(gateway.enterPortal(agent, 26)).thenReturn(true);
        when(gateway.observedByPlayer(agent)).thenReturn(false);

        AgentVictoriaRouteRuntime.travelStatus(entry, agent, 101000003, gateway, 1_000L);
        AgentVictoriaRouteRuntime.TravelOutcome waiting = AgentVictoriaRouteRuntime.travelStatus(
                entry, agent, 101000003, gateway, 3_000L);

        assertEquals(AgentVictoriaRouteRuntime.Status.MOVING, waiting.status());
        verify(gateway).stop(entry);
    }
}
