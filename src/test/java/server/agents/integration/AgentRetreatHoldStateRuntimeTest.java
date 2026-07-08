package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentRetreatHoldStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRetreatHoldStateRuntimeTest {
    @Test
    void adaptsRetreatHoldWithCopySafety() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Point hold = new Point(100, 200);

        assertFalse(AgentRetreatHoldStateRuntime.hasHold(entry));

        AgentRetreatHoldStateRuntime.setHold(entry, hold, 2_000L);

        Point stored = AgentRetreatHoldStateRuntime.holdPosition(entry);
        assertTrue(AgentRetreatHoldStateRuntime.hasHold(entry));
        assertTrue(AgentRetreatHoldStateRuntime.hasActiveHold(entry, 1_999L));
        assertFalse(AgentRetreatHoldStateRuntime.hasActiveHold(entry, 2_000L));
        assertEquals(2_000L, AgentRetreatHoldStateRuntime.holdUntilMs(entry));
        assertEquals(hold, stored);
        assertNotSame(hold, stored);

        hold.x = 500;
        stored.x = 600;

        assertEquals(new Point(100, 200), AgentRetreatHoldStateRuntime.holdPosition(entry));
    }

    @Test
    void measuresDistanceAndClearsHold() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentRetreatHoldStateRuntime.setHold(entry, new Point(100, 200), 2_000L);

        assertEquals(25, AgentRetreatHoldStateRuntime.distanceFromHoldX(entry, new Point(75, 200)));

        AgentRetreatHoldStateRuntime.clear(entry);

        assertFalse(AgentRetreatHoldStateRuntime.hasHold(entry));
        assertNull(AgentRetreatHoldStateRuntime.holdPosition(entry));
        assertEquals(0L, AgentRetreatHoldStateRuntime.holdUntilMs(entry));
    }
}
