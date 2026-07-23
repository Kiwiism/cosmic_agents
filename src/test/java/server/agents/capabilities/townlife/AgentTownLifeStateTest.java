package server.agents.capabilities.townlife;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTownLifeStateTest {
    @Test
    void startAndStopOwnTheWholeTownLifeSession() {
        AgentTownLifeState state = new AgentTownLifeState();

        state.start(1_000L, 8, LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID);

        assertTrue(state.enabled());
        assertEquals(AgentTownLifeState.Stage.TRAVEL_TO_TOWN, state.stage());
        assertEquals(AgentTownLifeState.VisitPhase.ARRIVING, state.visitPhase());
        assertFalse(state.initialPlacementComplete());
        assertTrue(state.nextActionAtMs() >= 1_500L);
        assertTrue(state.nextActionAtMs() <= 11_000L);

        state.select(AgentTownLifeState.Activity.SOCIAL, new Point(10, 20), 55, 0, 2_000L);
        assertEquals(AgentTownLifeState.Stage.MOVE_TO_ACTIVITY, state.stage());
        assertEquals(new Point(10, 20), state.target());
        assertEquals(55, state.targetCharacterId());

        state.transition(AgentTownLifeState.Stage.COMPLETE_ARRIVAL, 2_500L);
        assertEquals(AgentTownLifeState.VisitPhase.ERRAND, state.visitPhase());
        state.transition(AgentTownLifeState.Stage.SETTLING, 3_000L);
        assertEquals(AgentTownLifeState.VisitPhase.FREE_TIME, state.visitPhase());

        state.stop();

        assertFalse(state.enabled());
        assertEquals(AgentTownLifeState.Stage.DISABLED, state.stage());
        assertEquals(AgentTownLifeState.Activity.NONE, state.activity());
        assertEquals(AgentTownLifeState.VisitPhase.DEPARTING, state.visitPhase());
    }

    @Test
    void initialResponseDelayIsBoundedAndVariesByCharacter() {
        long first = AgentTownLifeState.initialResponseDelayMs(8);
        long second = AgentTownLifeState.initialResponseDelayMs(9);

        assertTrue(first >= 500L && first <= 10_000L);
        assertTrue(second >= 500L && second <= 10_000L);
        assertNotEquals(first, second);
    }

    @Test
    void boundedCapabilityRequestExpiresOnlyAfterFreeTimeBegins() {
        AgentTownLifeState state = new AgentTownLifeState();
        state.start(1_000L, 3, new AgentTownLifeVisitRequest(
                LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID,
                AgentTownLifeVisitRequest.Purpose.SUPPLIES,
                "restock complete", 5_000L));

        assertEquals(AgentTownLifeVisitRequest.Purpose.SUPPLIES, state.visitPurpose());
        assertFalse(state.freeTimeExpired(100_000L));

        state.transition(AgentTownLifeState.Stage.SETTLING, 2_000L);
        assertFalse(state.freeTimeExpired(6_999L));
        assertTrue(state.freeTimeExpired(7_000L));
    }
}
