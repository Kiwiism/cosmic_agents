package server.agents.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVictoriaRouteStateTest {
    @Test
    void activeTravelIsScopedToTheCurrentIntermediateMap() {
        AgentVictoriaRouteState state = new AgentVictoriaRouteState();

        state.markActiveTravel(101040000, 101010101);

        assertTrue(state.activeTravelIn(101040000));
        assertFalse(state.activeTravelIn(101030000));
        assertFalse(state.activeTravelIn(101010101));
    }

    @Test
    void portalSuccessStopsCombatUntilTheNextMapPlansItsPortalLeg() {
        AgentVictoriaRouteState state = new AgentVictoriaRouteState();
        state.markActiveTravel(101040000, 101010101);

        state.recordPortalSuccess(101030000, 1_000L, 1_500L);

        assertFalse(state.activeTravelIn(101040000));
        state.markActiveTravel(101030000, 101010101);
        assertTrue(state.activeTravelIn(101030000));
    }

    @Test
    void portalArrivalSettlesOnlyAtTheExpectedDestinationMap() {
        AgentVictoriaRouteState state = new AgentVictoriaRouteState();

        state.recordPortalSuccess(101000003, 1_000L, 1_500L);

        assertTrue(state.settlingAt(101000003, 2_000L));
        assertFalse(state.settlingAt(101000003, 2_500L));
    }

    @Test
    void scriptedArrivalWaitsForObserverThenProvidesAVisibleSettle() {
        AgentVictoriaRouteState state = new AgentVictoriaRouteState();

        state.recordPortalSuccess(101000003, 1_000L, 1_500L, true, 10_000L);

        assertTrue(state.settlingAt(101000003, 3_000L, false));
        assertTrue(state.settlingAt(101000003, 3_000L, true));
        assertTrue(state.settlingAt(101000003, 4_499L, true));
        assertFalse(state.settlingAt(101000003, 4_500L, true));
    }

    @Test
    void scriptedArrivalContinuesWhenObserverGraceExpires() {
        AgentVictoriaRouteState state = new AgentVictoriaRouteState();

        state.recordPortalSuccess(101000003, 1_000L, 1_500L, true, 10_000L);

        assertFalse(state.settlingAt(101000003, 11_000L, false));
    }
}
