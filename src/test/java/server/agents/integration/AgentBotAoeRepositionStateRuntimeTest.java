package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotAoeRepositionStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotAoeRepositionStateRuntimeTest {
    @Test
    void adaptsAoeRepositionAnchorWithCopySafety() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Point anchor = new Point(100, 200);

        assertFalse(AgentBotAoeRepositionStateRuntime.hasAnchor(entry));

        AgentBotAoeRepositionStateRuntime.setAnchor(entry, anchor, 2_000L);

        Point stored = AgentBotAoeRepositionStateRuntime.anchor(entry);
        assertTrue(AgentBotAoeRepositionStateRuntime.hasAnchor(entry));
        assertEquals(anchor, stored);
        assertNotSame(anchor, stored);
        assertEquals(2_000L, AgentBotAoeRepositionStateRuntime.deadlineMs(entry));

        anchor.x = 500;
        stored.x = 600;

        assertEquals(new Point(100, 200), AgentBotAoeRepositionStateRuntime.anchor(entry));
    }

    @Test
    void clearsAoeRepositionAnchorAndDeadline() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentBotAoeRepositionStateRuntime.setAnchor(entry, new Point(100, 200), 2_000L);
        AgentBotAoeRepositionStateRuntime.clear(entry);

        assertFalse(AgentBotAoeRepositionStateRuntime.hasAnchor(entry));
        assertNull(AgentBotAoeRepositionStateRuntime.anchor(entry));
        assertEquals(0L, AgentBotAoeRepositionStateRuntime.deadlineMs(entry));
    }

    @Test
    void detectsExpiryAndArrival() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentBotAoeRepositionStateRuntime.setAnchor(entry, new Point(100, 200), 2_000L);

        assertFalse(AgentBotAoeRepositionStateRuntime.isExpiredOrArrived(
                entry, new Point(10, 200), 2_000L, 20));
        assertTrue(AgentBotAoeRepositionStateRuntime.isExpiredOrArrived(
                entry, new Point(10, 200), 2_001L, 20));
        assertTrue(AgentBotAoeRepositionStateRuntime.isExpiredOrArrived(
                entry, new Point(85, 200), 2_000L, 20));

        AgentBotAoeRepositionStateRuntime.clear(entry);

        assertTrue(AgentBotAoeRepositionStateRuntime.isExpiredOrArrived(
                entry, new Point(10, 200), 2_000L, 20));
    }
}
