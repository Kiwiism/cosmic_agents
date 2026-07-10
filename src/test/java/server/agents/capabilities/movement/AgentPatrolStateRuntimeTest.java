package server.agents.capabilities.movement;



import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPatrolStateRuntimeTest {
    @Test
    void adaptsPatrolRegionAndWanderStateWithDefensiveCopies() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Point wander = new Point(100, 200);

        AgentPatrolStateRuntime.startPatrol(entry, 7, 100000000);
        AgentPatrolStateRuntime.setPatrolWanderTarget(entry, wander);
        wander.x = 999;

        assertTrue(AgentPatrolStateRuntime.hasPatrolRegion(entry));
        assertEquals(7, AgentPatrolStateRuntime.patrolRegionId(entry));
        assertEquals(100000000, AgentPatrolStateRuntime.patrolMapId(entry));
        assertEquals(new Point(100, 200), AgentPatrolStateRuntime.patrolWanderTarget(entry));

        Point exposed = AgentPatrolStateRuntime.patrolWanderTarget(entry);
        exposed.y = 999;

        assertEquals(new Point(100, 200), AgentPatrolStateRuntime.patrolWanderTarget(entry));
    }

    @Test
    void clearingPatrolResetsRegionMapAndWanderTarget() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentPatrolStateRuntime.startPatrol(entry, 7, 100000000);
        AgentPatrolStateRuntime.setPatrolWanderTarget(entry, new Point(100, 200));
        AgentPatrolStateRuntime.clearPatrol(entry);

        assertFalse(AgentPatrolStateRuntime.hasPatrolRegion(entry));
        assertEquals(-1, AgentPatrolStateRuntime.patrolRegionId(entry));
        assertEquals(-1, AgentPatrolStateRuntime.patrolMapId(entry));
        assertNull(AgentPatrolStateRuntime.patrolWanderTarget(entry));
    }

    @Test
    void clearsOnlyWhenMapChanges() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentPatrolStateRuntime.startPatrol(entry, 7, 100000000);

        assertFalse(AgentPatrolStateRuntime.clearPatrolIfMapChanged(entry, 100000000));
        assertTrue(AgentPatrolStateRuntime.hasPatrolRegion(entry));
        assertTrue(AgentPatrolStateRuntime.clearPatrolIfMapChanged(entry, 200000000));
        assertFalse(AgentPatrolStateRuntime.hasPatrolRegion(entry));
    }
}
