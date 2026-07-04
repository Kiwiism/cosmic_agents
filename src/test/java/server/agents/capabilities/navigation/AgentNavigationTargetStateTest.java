package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationTargetStateTest {
    @Test
    void defaultsPreserveLegacyBotEntryValues() {
        AgentNavigationTargetState state = new AgentNavigationTargetState();

        assertNull(state.position());
        assertFalse(state.hasPosition());
        assertEquals(-1, state.regionId());
        assertFalse(state.precise());
    }

    @Test
    void storesDefensivePositionCopies() {
        AgentNavigationTargetState state = new AgentNavigationTargetState();
        Point position = new Point(20, 30);

        state.setPosition(position);
        position.x = 99;
        Point exposed = state.position();
        exposed.y = 88;

        assertTrue(state.hasPosition());
        assertEquals(new Point(20, 30), state.position());
    }

    @Test
    void storesRegionAndPrecisionThenClears() {
        AgentNavigationTargetState state = new AgentNavigationTargetState();

        state.setPosition(new Point(1, 2));
        state.setRegionId(7);
        state.setPrecise(true);

        assertEquals(7, state.regionId());
        assertTrue(state.precise());

        state.clear();

        assertNull(state.position());
        assertEquals(-1, state.regionId());
        assertFalse(state.precise());
    }
}
