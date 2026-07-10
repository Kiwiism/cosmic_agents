package server.agents.capabilities.follow;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentOwnerMotionStateTest {
    @Test
    void defaultsPreserveLegacyAgentRuntimeEntryValues() {
        AgentOwnerMotionState state = new AgentOwnerMotionState();

        assertNull(state.lastOwnerPosition());
        assertEquals(0, state.observedOwnerStepX());
        assertEquals(0, state.observedOwnerStepY());
        assertFalse(state.observedOwnerMoved());
        assertTrue(state.ownerMostlyIdle());
    }

    @Test
    void storesLastOwnerPositionWithDefensiveCopies() {
        AgentOwnerMotionState state = new AgentOwnerMotionState();
        Point original = new Point(10, 20);

        state.setLastOwnerPosition(original);
        original.x = 99;
        Point stored = state.lastOwnerPosition();

        assertEquals(new Point(10, 20), stored);
        assertNotSame(stored, state.lastOwnerPosition());
    }

    @Test
    void updatesAndClearsObservedStep() {
        AgentOwnerMotionState state = new AgentOwnerMotionState();

        state.updateObservedOwnerStep(new Point(10, 20));

        assertEquals(0, state.observedOwnerStepX());
        assertEquals(0, state.observedOwnerStepY());

        state.setLastOwnerPosition(new Point(10, 20));
        state.updateObservedOwnerStep(new Point(15, 18));

        assertEquals(5, state.observedOwnerStepX());
        assertEquals(-2, state.observedOwnerStepY());
        assertEquals(5, state.maxObservedOwnerStep());
        assertTrue(state.observedOwnerMoved());
        assertFalse(state.ownerMostlyIdle());

        state.clearObservedOwnerStep();

        assertEquals(0, state.observedOwnerStepX());
        assertEquals(0, state.observedOwnerStepY());
    }
}
