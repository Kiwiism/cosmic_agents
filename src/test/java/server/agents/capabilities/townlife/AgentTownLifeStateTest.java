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
        assertFalse(state.initialPlacementComplete());
        assertTrue(state.nextActionAtMs() >= 1_500L);
        assertTrue(state.nextActionAtMs() <= 11_000L);

        state.select(AgentTownLifeState.Activity.SOCIAL, new Point(10, 20), 55, 0, 2_000L);
        assertEquals(AgentTownLifeState.Stage.MOVE_TO_ACTIVITY, state.stage());
        assertEquals(new Point(10, 20), state.target());
        assertEquals(55, state.targetCharacterId());

        state.stop();

        assertFalse(state.enabled());
        assertEquals(AgentTownLifeState.Stage.DISABLED, state.stage());
        assertEquals(AgentTownLifeState.Activity.NONE, state.activity());
    }

    @Test
    void initialResponseDelayIsBoundedAndVariesByCharacter() {
        long first = AgentTownLifeState.initialResponseDelayMs(8);
        long second = AgentTownLifeState.initialResponseDelayMs(9);

        assertTrue(first >= 500L && first <= 10_000L);
        assertTrue(second >= 500L && second <= 10_000L);
        assertNotEquals(first, second);
    }
}
