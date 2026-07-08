package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotFarmAnchorStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotFarmAnchorStateRuntimeTest {
    @Test
    void adaptsFarmAnchorStateWithDefensiveCopies() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Point anchor = new Point(300, 100);

        AgentBotFarmAnchorStateRuntime.setFarmAnchor(entry, anchor, 100000000);
        anchor.x = 999;

        assertTrue(AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry));
        assertEquals(100000000, AgentBotFarmAnchorStateRuntime.farmAnchorMapId(entry));
        assertEquals(new Point(300, 100), AgentBotFarmAnchorStateRuntime.farmAnchor(entry));
        assertEquals(new Point(300, 100),
                AgentBotFarmAnchorStateRuntime.farmAnchorInMap(entry, 100000000));
        assertNull(AgentBotFarmAnchorStateRuntime.farmAnchorInMap(entry, 200000000));

        Point exposed = AgentBotFarmAnchorStateRuntime.farmAnchor(entry);
        exposed.y = 999;

        assertEquals(new Point(300, 100), AgentBotFarmAnchorStateRuntime.farmAnchor(entry));
    }

    @Test
    void clearingFarmAnchorResetsMapId() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentBotFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(300, 100), 100000000);
        AgentBotFarmAnchorStateRuntime.clearFarmAnchor(entry);

        assertFalse(AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry));
        assertEquals(-1, AgentBotFarmAnchorStateRuntime.farmAnchorMapId(entry));
        assertNull(AgentBotFarmAnchorStateRuntime.farmAnchor(entry));

        AgentBotFarmAnchorStateRuntime.setFarmAnchor(entry, null, 100000000);

        assertFalse(AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry));
        assertEquals(-1, AgentBotFarmAnchorStateRuntime.farmAnchorMapId(entry));
    }

    @Test
    void clearsOnlyWhenMapChanges() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentBotFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(300, 100), 100000000);

        assertFalse(AgentBotFarmAnchorStateRuntime.clearFarmAnchorIfMapChanged(entry, 100000000));
        assertTrue(AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry));
        assertTrue(AgentBotFarmAnchorStateRuntime.clearFarmAnchorIfMapChanged(entry, 200000000));
        assertFalse(AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry));
    }
}
