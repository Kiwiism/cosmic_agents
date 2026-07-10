package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPatrolStateTest {
    @Test
    void storesDefensivePatrolRegionAndWanderTarget() {
        AgentPatrolState state = new AgentPatrolState();

        assertFalse(state.hasRegion());
        assertEquals(-1, state.regionId());
        assertEquals(-1, state.mapId());
        assertNull(state.wanderTarget());

        state.setRegion(7, 100000000);
        Point target = new Point(100, 200);
        state.setWanderTarget(target);
        target.x = 999;

        assertTrue(state.hasRegion());
        assertEquals(7, state.regionId());
        assertEquals(100000000, state.mapId());
        assertEquals(new Point(100, 200), state.wanderTarget());

        Point exposed = state.wanderTarget();
        exposed.y = 999;

        assertEquals(new Point(100, 200), state.wanderTarget());

        state.clear();

        assertFalse(state.hasRegion());
        assertEquals(-1, state.regionId());
        assertEquals(-1, state.mapId());
        assertNull(state.wanderTarget());
    }
}
