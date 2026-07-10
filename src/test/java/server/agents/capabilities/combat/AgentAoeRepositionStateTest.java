package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAoeRepositionStateTest {
    @Test
    void storesAnchorWithDefensiveCopies() {
        AgentAoeRepositionState state = new AgentAoeRepositionState();
        Point anchor = new Point(100, 200);

        state.setAnchor(anchor, 2_000L);
        anchor.x = 999;

        assertTrue(state.hasAnchor());
        assertEquals(new Point(100, 200), state.anchor());
        assertEquals(2_000L, state.deadlineMs());

        Point exposed = state.anchor();
        exposed.y = 999;

        assertEquals(new Point(100, 200), state.anchor());
    }

    @Test
    void nullAnchorClearsDeadline() {
        AgentAoeRepositionState state = new AgentAoeRepositionState();

        state.setAnchor(new Point(100, 200), 2_000L);
        state.setAnchor(null, 3_000L);

        assertFalse(state.hasAnchor());
        assertNull(state.anchor());
        assertEquals(0L, state.deadlineMs());
    }

    @Test
    void detectsExpiryAndArrivalWithLegacyStrictDeadline() {
        AgentAoeRepositionState state = new AgentAoeRepositionState();

        state.setAnchor(new Point(100, 200), 2_000L);

        assertFalse(state.expiredOrArrived(new Point(10, 200), 2_000L, 20));
        assertTrue(state.expiredOrArrived(new Point(10, 200), 2_001L, 20));
        assertTrue(state.expiredOrArrived(new Point(85, 200), 2_000L, 20));

        state.clear();

        assertTrue(state.expiredOrArrived(new Point(10, 200), 2_000L, 20));
    }
}
