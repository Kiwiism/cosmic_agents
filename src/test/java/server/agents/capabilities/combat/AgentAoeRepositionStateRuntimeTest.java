package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAoeRepositionStateRuntimeTest {
    @Test
    void adaptsAoeRepositionAnchorWithCopySafety() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Point anchor = new Point(100, 200);

        assertFalse(AgentAoeRepositionStateRuntime.hasAnchor(entry));

        AgentAoeRepositionStateRuntime.setAnchor(entry, anchor, 2_000L);

        Point stored = AgentAoeRepositionStateRuntime.anchor(entry);
        assertTrue(AgentAoeRepositionStateRuntime.hasAnchor(entry));
        assertEquals(anchor, stored);
        assertNotSame(anchor, stored);
        assertEquals(2_000L, AgentAoeRepositionStateRuntime.deadlineMs(entry));

        anchor.x = 500;
        stored.x = 600;

        assertEquals(new Point(100, 200), AgentAoeRepositionStateRuntime.anchor(entry));
    }

    @Test
    void clearsAoeRepositionAnchorAndDeadline() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentAoeRepositionStateRuntime.setAnchor(entry, new Point(100, 200), 2_000L);
        AgentAoeRepositionStateRuntime.clear(entry);

        assertFalse(AgentAoeRepositionStateRuntime.hasAnchor(entry));
        assertNull(AgentAoeRepositionStateRuntime.anchor(entry));
        assertEquals(0L, AgentAoeRepositionStateRuntime.deadlineMs(entry));
    }

    @Test
    void detectsExpiryAndArrival() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentAoeRepositionStateRuntime.setAnchor(entry, new Point(100, 200), 2_000L);

        assertFalse(AgentAoeRepositionStateRuntime.isExpiredOrArrived(
                entry, new Point(10, 200), 2_000L, 20));
        assertTrue(AgentAoeRepositionStateRuntime.isExpiredOrArrived(
                entry, new Point(10, 200), 2_001L, 20));
        assertTrue(AgentAoeRepositionStateRuntime.isExpiredOrArrived(
                entry, new Point(85, 200), 2_000L, 20));

        AgentAoeRepositionStateRuntime.clear(entry);

        assertTrue(AgentAoeRepositionStateRuntime.isExpiredOrArrived(
                entry, new Point(10, 200), 2_000L, 20));
    }
}
