package server.agents.capabilities.townlife;

import client.Character;
import constants.id.ItemId;
import org.junit.jupiter.api.Test;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentForegroundPauseRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;
import server.agents.runtime.simulation.AgentAbstractExecutionScope;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTownLifeRuntimeTest {
    @Test
    void lifecycleEventsRetainCallerCorrelationAcrossAGracefulSession() {
        Character agent = localAgent(34, LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID,
                new Point(0, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);
        List<AgentTownLifeLifecycleEvent> events = new ArrayList<>();
        AgentSessionEventRuntime.bus(entry).subscribe(
                AgentTownLifeLifecycleEvent.TYPE,
                event -> events.add((AgentTownLifeLifecycleEvent) event));
        AgentTownLifeEntryRequest request = AgentTownLifeEntryRequest.external(
                "visit-34", "test-plan", AgentTownLifeVisitRequest.leisure(agent.getMapId()));

        AgentTownLifeSessionResult started = AgentTownLifeRuntime.requestSession(
                entry, agent, request, AgentTownLifeAdmissionMode.MANUAL_ONLY, 1_000L, 34);
        AgentTownLifeRuntime.requestExit(entry, agent, AgentTownLifeExitRequest.graceful(
                started.handle(), "complete", 1_100L, 2_000L));
        AgentSessionEventRuntime.bus(entry).drain(20);

        assertEquals(List.of(
                        AgentTownLifeLifecycleEvent.Phase.STARTED,
                        AgentTownLifeLifecycleEvent.Phase.EXIT_REQUESTED,
                        AgentTownLifeLifecycleEvent.Phase.EXITED),
                events.stream().map(AgentTownLifeLifecycleEvent::phase).toList());
        assertTrue(events.stream().allMatch(event -> "visit-34".equals(event.requestId())
                && "test-plan".equals(event.callerId())
                && started.handle().sessionId().equals(event.sessionId())));
    }

    @Test
    void entryRequestsAreIdempotentForTheirOwnerAndRejectACompetingOwner() {
        Character agent = localAgent(30, LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID,
                new Point(0, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);
        AgentTownLifeEntryRequest first = AgentTownLifeEntryRequest.external(
                "visit-30", "test-plan", AgentTownLifeVisitRequest.leisure(agent.getMapId()));

        AgentTownLifeSessionResult started = AgentTownLifeRuntime.requestSession(
                entry, agent, first, AgentTownLifeAdmissionMode.MANUAL_ONLY, 1_000L, 30);
        AgentTownLifeSessionResult repeated = AgentTownLifeRuntime.requestSession(
                entry, agent, first, AgentTownLifeAdmissionMode.MANUAL_ONLY, 1_100L, 30);
        AgentTownLifeSessionResult competing = AgentTownLifeRuntime.requestSession(
                entry, agent, AgentTownLifeEntryRequest.external(
                        "visit-31", "other-plan",
                        AgentTownLifeVisitRequest.leisure(agent.getMapId())),
                AgentTownLifeAdmissionMode.MANUAL_ONLY, 1_200L, 30);

        assertEquals(AgentTownLifeSessionResult.Status.STARTED, started.status());
        assertEquals(AgentTownLifeSessionResult.Status.ALREADY_ACTIVE_SAME_REQUEST,
                repeated.status());
        assertEquals(started.handle(), repeated.handle());
        assertEquals(AgentTownLifeSessionResult.Status.REJECTED_ALREADY_ACTIVE_OTHER_REQUEST,
                competing.status());
        AgentTownLifeRuntime.forceStop(entry, agent, "test cleanup");
    }

    @Test
    void legacyAdmissionRemainsIdempotentForExistingCallers() {
        Character agent = localAgent(35, LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID,
                new Point(0, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);

        AgentTownLifeSessionResult first = AgentTownLifeRuntime.requestLocal(
                entry, agent, AgentTownLifeVisitRequest.leisure(agent.getMapId()),
                AgentTownLifeAdmissionMode.MANUAL_ONLY, 1_000L, 35);
        AgentTownLifeSessionResult repeated = AgentTownLifeRuntime.requestLocal(
                entry, agent, AgentTownLifeVisitRequest.leisure(agent.getMapId()),
                AgentTownLifeAdmissionMode.MANUAL_ONLY, 2_000L, 35);

        assertEquals(AgentTownLifeSessionResult.Status.STARTED, first.status());
        assertEquals(AgentTownLifeSessionResult.Status.ALREADY_ACTIVE, repeated.status());
        assertEquals(first.handle(), repeated.handle());
        AgentTownLifeRuntime.forceStop(entry, agent, "test cleanup");
    }

    @Test
    void gracefulExitFinishesTheCommittedActivityBeforeResumingForegroundWork() {
        Character agent = localAgent(32, LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID,
                new Point(0, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);
        AgentTownLifeEntryRequest request = AgentTownLifeEntryRequest.external(
                "visit-32", "test-plan", AgentTownLifeVisitRequest.leisure(agent.getMapId()));
        AgentTownLifeSessionResult started = AgentTownLifeRuntime.requestSession(
                entry, agent, request, AgentTownLifeAdmissionMode.MANUAL_ONLY, 1_000L, 32);
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        state.select(AgentTownLifeState.Activity.LINGER, new Point(0, 0), 0, 1_000L);
        state.beginDwell(2_000L);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);

        AgentTownLifeExitResult exit = AgentTownLifeRuntime.requestExit(entry, agent,
                AgentTownLifeExitRequest.graceful(
                        started.handle(), "next objective", 1_100L, 5_000L));

        assertEquals(AgentTownLifeExitResult.Status.EXIT_REQUESTED, exit.status());
        assertTrue(AgentTownLifeRuntime.active(entry));
        assertTrue(AgentTownLifeRuntime.tick(entry, agent, 1_500L, gateway));
        assertTrue(AgentTownLifeRuntime.active(entry));
        assertTrue(AgentTownLifeRuntime.tick(entry, agent, 2_000L, gateway));
        assertFalse(AgentTownLifeRuntime.active(entry));
        assertFalse(AgentForegroundPauseRuntime.paused(entry));
    }

    @Test
    void gracefulExitDeadlineBoundsAnActivityThatCannotFinish() {
        Character agent = localAgent(33, LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID,
                new Point(0, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);
        AgentTownLifeEntryRequest request = AgentTownLifeEntryRequest.external(
                "visit-33", "test-plan", AgentTownLifeVisitRequest.leisure(agent.getMapId()));
        AgentTownLifeSessionResult started = AgentTownLifeRuntime.requestSession(
                entry, agent, request, AgentTownLifeAdmissionMode.MANUAL_ONLY, 1_000L, 33);
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        state.select(AgentTownLifeState.Activity.STROLL, new Point(500, 0), 0, 1_000L);

        AgentTownLifeRuntime.requestExit(entry, agent, AgentTownLifeExitRequest.graceful(
                started.handle(), "deadline", 1_100L, 1_200L));

        assertTrue(AgentTownLifeRuntime.tick(
                entry, agent, 1_200L, mock(PrimitiveCapabilityGateway.class)));
        assertFalse(AgentTownLifeRuntime.active(entry));
    }

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
