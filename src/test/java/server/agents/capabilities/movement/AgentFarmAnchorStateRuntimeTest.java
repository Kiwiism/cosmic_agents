package server.agents.capabilities.movement;



import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFarmAnchorStateRuntimeTest {
    @Test
    void adaptsFarmAnchorStateWithDefensiveCopies() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Point anchor = new Point(300, 100);

        AgentFarmAnchorStateRuntime.setFarmAnchor(entry, anchor, 100000000);
        anchor.x = 999;

        assertTrue(AgentFarmAnchorStateRuntime.hasFarmAnchor(entry));
        assertEquals(100000000, AgentFarmAnchorStateRuntime.farmAnchorMapId(entry));
        assertEquals(new Point(300, 100), AgentFarmAnchorStateRuntime.farmAnchor(entry));
        assertEquals(new Point(300, 100),
                AgentFarmAnchorStateRuntime.farmAnchorInMap(entry, 100000000));
        assertNull(AgentFarmAnchorStateRuntime.farmAnchorInMap(entry, 200000000));

        Point exposed = AgentFarmAnchorStateRuntime.farmAnchor(entry);
        exposed.y = 999;

        assertEquals(new Point(300, 100), AgentFarmAnchorStateRuntime.farmAnchor(entry));
    }

    @Test
    void clearingFarmAnchorResetsMapId() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(300, 100), 100000000);
        AgentFarmAnchorStateRuntime.clearFarmAnchor(entry);

        assertFalse(AgentFarmAnchorStateRuntime.hasFarmAnchor(entry));
        assertEquals(-1, AgentFarmAnchorStateRuntime.farmAnchorMapId(entry));
        assertNull(AgentFarmAnchorStateRuntime.farmAnchor(entry));

        AgentFarmAnchorStateRuntime.setFarmAnchor(entry, null, 100000000);

        assertFalse(AgentFarmAnchorStateRuntime.hasFarmAnchor(entry));
        assertEquals(-1, AgentFarmAnchorStateRuntime.farmAnchorMapId(entry));
    }

    @Test
    void clearsOnlyWhenMapChanges() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(300, 100), 100000000);

        assertFalse(AgentFarmAnchorStateRuntime.clearFarmAnchorIfMapChanged(entry, 100000000));
        assertTrue(AgentFarmAnchorStateRuntime.hasFarmAnchor(entry));
        assertTrue(AgentFarmAnchorStateRuntime.clearFarmAnchorIfMapChanged(entry, 200000000));
        assertFalse(AgentFarmAnchorStateRuntime.hasFarmAnchor(entry));
    }
}
