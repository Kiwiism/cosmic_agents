package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentMapStateRuntime;
import server.maps.Foothold;

import java.awt.Point;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMapStateRuntimeTest {
    @Test
    void tracksCurrentMapAndFootholds() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Foothold foothold = new Foothold(new Point(0, 100), new Point(100, 100), 1);
        Map<Integer, Foothold> footholds = new HashMap<>();
        footholds.put(1, foothold);

        AgentMapStateRuntime.setMapTracking(entry, 100000000, footholds);

        assertEquals(100000000, AgentMapStateRuntime.lastMapId(entry));
        assertTrue(AgentMapStateRuntime.isTrackingMap(entry, 100000000));
        assertEquals(foothold, AgentMapStateRuntime.footholdIndex(entry).get(1));
    }

    @Test
    void copiesFootholdIndexOnWrite() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Map<Integer, Foothold> footholds = new HashMap<>();
        footholds.put(1, new Foothold(new Point(0, 100), new Point(100, 100), 1));

        AgentMapStateRuntime.setMapTracking(entry, 100000000, footholds);
        footholds.clear();

        assertEquals(1, AgentMapStateRuntime.footholdIndex(entry).size());
    }

    @Test
    void exposesReadOnlyFootholdIndex() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentMapStateRuntime.setMapTracking(entry, 100000000, Map.of());

        assertThrows(UnsupportedOperationException.class,
                () -> AgentMapStateRuntime.footholdIndex(entry).put(1,
                        new Foothold(new Point(0, 100), new Point(100, 100), 1)));
    }

    @Test
    void handlesNullFootholdIndexAsEmpty() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentMapStateRuntime.setMapTracking(entry, 100000000, null);

        assertEquals(100000000, AgentMapStateRuntime.lastMapId(entry));
        assertTrue(AgentMapStateRuntime.footholdIndex(entry).isEmpty());
        assertFalse(AgentMapStateRuntime.isTrackingMap(entry, 100000001));
    }
}
