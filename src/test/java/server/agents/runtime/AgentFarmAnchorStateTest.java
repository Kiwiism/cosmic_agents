package server.agents.runtime;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFarmAnchorStateTest {
    @Test
    void storesDefensiveAnchorCopyAndMap() {
        AgentFarmAnchorState state = new AgentFarmAnchorState();
        Point anchor = new Point(300, 100);

        assertFalse(state.hasAnchor());
        assertNull(state.anchor());
        assertEquals(-1, state.mapId());

        state.setAnchor(anchor, 100000000);
        anchor.x = 999;

        assertTrue(state.hasAnchor());
        assertEquals(new Point(300, 100), state.anchor());
        assertEquals(100000000, state.mapId());

        Point exposed = state.anchor();
        exposed.y = 999;

        assertEquals(new Point(300, 100), state.anchor());

        state.clear();

        assertFalse(state.hasAnchor());
        assertNull(state.anchor());
        assertEquals(-1, state.mapId());
    }
}
