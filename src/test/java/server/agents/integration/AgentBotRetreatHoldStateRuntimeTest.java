package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotRetreatHoldStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotRetreatHoldStateRuntimeTest {
    @Test
    void adaptsRetreatHoldWithCopySafety() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Point hold = new Point(100, 200);

        assertFalse(AgentBotRetreatHoldStateRuntime.hasHold(entry));

        AgentBotRetreatHoldStateRuntime.setHold(entry, hold, 2_000L);

        Point stored = AgentBotRetreatHoldStateRuntime.holdPosition(entry);
        assertTrue(AgentBotRetreatHoldStateRuntime.hasHold(entry));
        assertTrue(AgentBotRetreatHoldStateRuntime.hasActiveHold(entry, 1_999L));
        assertFalse(AgentBotRetreatHoldStateRuntime.hasActiveHold(entry, 2_000L));
        assertEquals(2_000L, AgentBotRetreatHoldStateRuntime.holdUntilMs(entry));
        assertEquals(hold, stored);
        assertNotSame(hold, stored);

        hold.x = 500;
        stored.x = 600;

        assertEquals(new Point(100, 200), AgentBotRetreatHoldStateRuntime.holdPosition(entry));
    }

    @Test
    void measuresDistanceAndClearsHold() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentBotRetreatHoldStateRuntime.setHold(entry, new Point(100, 200), 2_000L);

        assertEquals(25, AgentBotRetreatHoldStateRuntime.distanceFromHoldX(entry, new Point(75, 200)));

        AgentBotRetreatHoldStateRuntime.clear(entry);

        assertFalse(AgentBotRetreatHoldStateRuntime.hasHold(entry));
        assertNull(AgentBotRetreatHoldStateRuntime.holdPosition(entry));
        assertEquals(0L, AgentBotRetreatHoldStateRuntime.holdUntilMs(entry));
    }
}
