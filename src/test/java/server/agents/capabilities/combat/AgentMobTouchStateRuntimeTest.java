package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentMobTouchStateRuntimeTest {
    @Test
    void adaptsSameMapMobTouchCheckpointState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertNull(AgentMobTouchStateRuntime.previousCheckPositionOnMap(entry, 100));

        AgentMobTouchStateRuntime.rememberCheck(entry, new Point(80, 200), 100);

        assertEquals(new Point(80, 200), AgentMobTouchStateRuntime.previousCheckPositionOnMap(entry, 100));
        assertNull(AgentMobTouchStateRuntime.previousCheckPositionOnMap(entry, 101));
    }

    @Test
    void returnsDefensivePositionCopy() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Point remembered = new Point(80, 200);

        AgentMobTouchStateRuntime.rememberCheck(entry, remembered, 100);
        remembered.x = 1;
        Point returned = AgentMobTouchStateRuntime.previousCheckPositionOnMap(entry, 100);
        returned.x = 2;

        assertEquals(new Point(80, 200), AgentMobTouchStateRuntime.previousCheckPositionOnMap(entry, 100));
    }
}
