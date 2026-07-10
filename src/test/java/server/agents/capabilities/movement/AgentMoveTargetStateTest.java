package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMoveTargetStateTest {
    @Test
    void storesTargetWithDefensiveCopies() {
        AgentMoveTargetState state = new AgentMoveTargetState();
        Point target = new Point(100, 200);

        state.setTarget(target, false);
        target.x = 999;

        assertTrue(state.hasTarget());
        assertFalse(state.precise());
        assertEquals(new Point(100, 200), state.target());
        assertTrue(state.targetEquals(new Point(100, 200)));

        Point exposed = state.target();
        exposed.y = 999;

        assertEquals(new Point(100, 200), state.target());
    }

    @Test
    void nullTargetClearsPreciseFlag() {
        AgentMoveTargetState state = new AgentMoveTargetState();

        state.setTarget(new Point(100, 100), true);
        state.setTarget(null, true);

        assertFalse(state.hasTarget());
        assertFalse(state.precise());
        assertNull(state.target());
    }

    @Test
    void clearResetsTargetAndPrecision() {
        AgentMoveTargetState state = new AgentMoveTargetState();

        state.setTarget(new Point(100, 100), true);
        state.clear();

        assertFalse(state.hasTarget());
        assertFalse(state.precise());
        assertNull(state.target());
    }
}
