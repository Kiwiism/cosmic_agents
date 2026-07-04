package server.agents.runtime;

import org.junit.jupiter.api.Test;
import server.maps.Foothold;

import java.awt.Point;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMapTrackingStateTest {
    @Test
    void ownsCurrentMapAndFootholdIndexSnapshot() {
        AgentMapTrackingState state = new AgentMapTrackingState();
        Foothold foothold = new Foothold(new Point(0, 100), new Point(100, 100), 1);
        Map<Integer, Foothold> footholds = new HashMap<>();
        footholds.put(1, foothold);

        state.setMapTracking(100000000, footholds);
        footholds.clear();

        assertEquals(100000000, state.lastMapId());
        assertEquals(foothold, state.footholdIndex().get(1));
        assertEquals(1, state.footholdIndex().size());
        assertThrows(UnsupportedOperationException.class,
                () -> state.footholdIndex().put(2, new Foothold(new Point(0, 50), new Point(100, 50), 2)));
    }

    @Test
    void treatsNullFootholdIndexAsEmpty() {
        AgentMapTrackingState state = new AgentMapTrackingState();

        state.setMapTracking(100000000, null);

        assertEquals(100000000, state.lastMapId());
        assertTrue(state.footholdIndex().isEmpty());
    }
}
