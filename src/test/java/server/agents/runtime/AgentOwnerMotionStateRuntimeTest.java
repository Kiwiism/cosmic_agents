package server.agents.runtime;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentOwnerMotionStateRuntimeTest {
    @Test
    void adaptsLastOwnerPositionWithDefensiveCopies() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Point original = new Point(10, 20);

        assertNull(AgentOwnerMotionStateRuntime.lastOwnerPosition(entry));

        AgentOwnerMotionStateRuntime.rememberOwnerPosition(entry, original);
        original.x = 99;
        Point stored = AgentOwnerMotionStateRuntime.lastOwnerPosition(entry);

        assertEquals(new Point(10, 20), stored);
        assertNotSame(stored, AgentOwnerMotionStateRuntime.lastOwnerPosition(entry));
    }

    @Test
    void updatesObservedOwnerStepFromLastPosition() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentOwnerMotionStateRuntime.updateObservedOwnerStep(entry, new Point(10, 20));

        assertEquals(0, AgentOwnerMotionStateRuntime.observedOwnerStepX(entry));
        assertEquals(0, AgentOwnerMotionStateRuntime.observedOwnerStepY(entry));
        assertFalse(AgentOwnerMotionStateRuntime.observedOwnerMoved(entry));
        assertTrue(AgentOwnerMotionStateRuntime.ownerMostlyIdle(entry));

        AgentOwnerMotionStateRuntime.rememberOwnerPosition(entry, new Point(10, 20));
        AgentOwnerMotionStateRuntime.updateObservedOwnerStep(entry, new Point(15, 18));

        assertEquals(5, AgentOwnerMotionStateRuntime.observedOwnerStepX(entry));
        assertEquals(-2, AgentOwnerMotionStateRuntime.observedOwnerStepY(entry));
        assertEquals(5, AgentOwnerMotionStateRuntime.maxObservedOwnerStep(entry));
        assertTrue(AgentOwnerMotionStateRuntime.observedOwnerMoved(entry));
        assertFalse(AgentOwnerMotionStateRuntime.ownerMostlyIdle(entry));

        AgentOwnerMotionStateRuntime.clearObservedOwnerStep(entry);

        assertFalse(AgentOwnerMotionStateRuntime.observedOwnerMoved(entry));
        assertTrue(AgentOwnerMotionStateRuntime.ownerMostlyIdle(entry));
    }
}
