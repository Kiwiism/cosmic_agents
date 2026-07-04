package server.agents.runtime;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRetreatHoldStateTest {
    @Test
    void storesHoldWithDefensiveCopies() {
        AgentRetreatHoldState state = new AgentRetreatHoldState();
        Point hold = new Point(100, 200);

        state.set(hold, 2_000L);
        hold.x = 999;

        assertTrue(state.hasHold());
        assertTrue(state.active(1_999L));
        assertFalse(state.active(2_000L));
        assertEquals(2_000L, state.untilMs());
        assertEquals(new Point(100, 200), state.position());

        Point exposed = state.position();
        exposed.y = 999;

        assertEquals(new Point(100, 200), state.position());
    }

    @Test
    void nullHoldClearsExpiry() {
        AgentRetreatHoldState state = new AgentRetreatHoldState();

        state.set(new Point(100, 200), 2_000L);
        state.set(null, 3_000L);

        assertFalse(state.hasHold());
        assertFalse(state.active(1_000L));
        assertNull(state.position());
        assertEquals(0L, state.untilMs());
    }

    @Test
    void clearResetsHold() {
        AgentRetreatHoldState state = new AgentRetreatHoldState();

        state.set(new Point(100, 200), 2_000L);
        state.clear();

        assertFalse(state.hasHold());
        assertNull(state.position());
        assertEquals(0L, state.untilMs());
    }
}
