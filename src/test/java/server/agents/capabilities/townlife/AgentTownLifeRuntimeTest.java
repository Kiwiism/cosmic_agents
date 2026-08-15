package server.agents.capabilities.townlife;

import client.Character;
import constants.id.ItemId;
import org.junit.jupiter.api.Test;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentForegroundPauseRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.simulation.AgentAbstractExecutionScope;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTownLifeRuntimeTest {
    @Test
    void localStartAndStopPauseAndResumeTheForegroundPlanClock() {
        Character agent = localAgent(31, LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID,
                new Point(0, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);

        AgentTownLifeSessionResult result = AgentTownLifeRuntime.requestLocal(
                entry, agent, AgentTownLifeVisitRequest.leisure(agent.getMapId()),
                AgentTownLifeAdmissionMode.MANUAL_ONLY, 1_000L, agent.getId());

        assertTrue(result.started());
        assertTrue(AgentForegroundPauseRuntime.paused(entry));
        assertEquals(AgentAbstractExecutionScope.TOWN_LIFE,
                entry.simulationState().abstractExecutionScope());
        assertEquals(AgentTownLifeState.Stage.SETTLING,
                entry.capabilityStates().require(AgentTownLifeState.STATE_KEY).stage());

        AgentTownLifeRuntime.stop(entry, agent);

        assertFalse(AgentForegroundPauseRuntime.paused(entry));
        assertEquals(AgentAbstractExecutionScope.NONE,
                entry.simulationState().abstractExecutionScope());
    }

    @Test
    void rejectsARequestUntilTravelPlacesTheAgentInTheTown() {
        Character agent = localAgent(12, 60000, new Point(0, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);

        AgentTownLifeSessionResult result = AgentTownLifeRuntime.requestLocal(
                entry, agent,
                AgentTownLifeVisitRequest.leisure(LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID),
                AgentTownLifeAdmissionMode.MANUAL_ONLY, 1_000L, agent.getId());

        assertEquals(AgentTownLifeSessionResult.Status.REJECTED_NOT_LOCAL, result.status());
        assertFalse(AgentTownLifeRuntime.active(entry));
    }

    @Test
    void leavingTheAuthoredTownEndsTheLocalSessionInsteadOfRoutingBack() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(18);
        when(agent.getMapId()).thenReturn(100000000, 100000001);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        when(agent.getChair()).thenReturn(-1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);
        AgentTownLifeRuntime.requestLocal(entry, agent,
                AgentTownLifeVisitRequest.leisure(100000000),
                AgentTownLifeAdmissionMode.MANUAL_ONLY, 0L, agent.getId());
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);

        assertTrue(AgentTownLifeRuntime.tick(entry, agent, 1L, gateway));

        assertFalse(AgentTownLifeRuntime.active(entry));
        verify(gateway, never()).travelTo(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void usesNativeMapSeatWithoutEquippingRelaxerAtBenchSpot() {
        Character agent = localAgent(27, LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID,
                new Point(2_404, 525));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        state.start(0L, 0, LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID);
        state.select(AgentTownLifeState.Activity.REST, new Point(2_404, 525), 0, 0L);
        state.beginDwell(10_000L);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);

        assertTrue(AgentTownLifeRuntime.tick(entry, agent, 1L, gateway));
        assertTrue(AgentTownLifeRuntime.tick(entry, agent, 800L, gateway));

        verify(gateway).sitMapSeat(agent, 0, new Point(2_404, 525));
        verify(gateway, never()).itemCount(agent, ItemId.RELAXER);
    }

    @Test
    void doesNotSitOnAdjacentUpperPlatformNearGroundRestSpot() {
        Character agent = localAgent(28, LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID,
                new Point(3_240, 452));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        state.start(0L, 0, LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID);
        Point groundRestSpot = new Point(3_240, 518);
        state.select(AgentTownLifeState.Activity.REST, groundRestSpot, 0, 0L);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.grounded(agent)).thenReturn(true);

        assertFalse(AgentTownLifeRuntime.tick(entry, agent, 1L, gateway));

        verify(gateway).navigate(entry, groundRestSpot, false);
        verify(gateway, never()).sitChair(agent, ItemId.RELAXER);
    }

    @Test
    void abandonsAStalledTownDestinationAndAllowsAReplan() {
        Character agent = localAgent(29, LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID,
                new Point(0, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        state.start(0L, 0, LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID);
        state.select(AgentTownLifeState.Activity.STROLL, new Point(500, 0),
                0, "wander:test", 0L);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.grounded(agent)).thenReturn(true);

        assertFalse(AgentTownLifeRuntime.tick(entry, agent, 1L, gateway));
        assertTrue(AgentTownLifeRuntime.tick(entry, agent, 8_001L, gateway));

        assertEquals(AgentTownLifeState.Stage.CHOOSE_ACTIVITY, state.stage());
        verify(gateway).stop(entry);
    }

    private static Character localAgent(int id, int mapId, Point position) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(id);
        when(agent.getMapId()).thenReturn(mapId);
        when(agent.getPosition()).thenReturn(position);
        when(agent.getChair()).thenReturn(-1);
        return agent;
    }
}
