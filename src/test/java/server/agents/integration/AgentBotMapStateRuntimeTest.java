package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMapStateRuntime;
import server.maps.Foothold;

import java.awt.Point;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotMapStateRuntimeTest {
    @Test
    void tracksCurrentMapAndFootholds() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Foothold foothold = new Foothold(new Point(0, 100), new Point(100, 100), 1);
        Map<Integer, Foothold> footholds = new HashMap<>();
        footholds.put(1, foothold);

        AgentBotMapStateRuntime.setMapTracking(entry, 100000000, footholds);

        assertEquals(100000000, AgentBotMapStateRuntime.lastMapId(entry));
        assertTrue(AgentBotMapStateRuntime.isTrackingMap(entry, 100000000));
        assertEquals(foothold, AgentBotMapStateRuntime.footholdIndex(entry).get(1));
    }

    @Test
    void copiesFootholdIndexOnWrite() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Map<Integer, Foothold> footholds = new HashMap<>();
        footholds.put(1, new Foothold(new Point(0, 100), new Point(100, 100), 1));

        AgentBotMapStateRuntime.setMapTracking(entry, 100000000, footholds);
        footholds.clear();

        assertEquals(1, AgentBotMapStateRuntime.footholdIndex(entry).size());
    }

    @Test
    void exposesReadOnlyFootholdIndex() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentBotMapStateRuntime.setMapTracking(entry, 100000000, Map.of());

        assertThrows(UnsupportedOperationException.class,
                () -> AgentBotMapStateRuntime.footholdIndex(entry).put(1,
                        new Foothold(new Point(0, 100), new Point(100, 100), 1)));
    }

    @Test
    void handlesNullFootholdIndexAsEmpty() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentBotMapStateRuntime.setMapTracking(entry, 100000000, null);

        assertEquals(100000000, AgentBotMapStateRuntime.lastMapId(entry));
        assertTrue(AgentBotMapStateRuntime.footholdIndex(entry).isEmpty());
        assertFalse(AgentBotMapStateRuntime.isTrackingMap(entry, 100000001));
    }
}
