package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import server.agents.integration.AgentClimbStateRuntime;

import server.agents.integration.AgentMovementStateRuntime;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentAirshowStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAirshowStateRuntimeTest {
    @Test
    void adaptsAirshowLifecycleAndTrailTiming() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentAirshowStateRuntime.active(entry));

        AgentAirshowStateRuntime.start(entry);

        assertTrue(AgentAirshowStateRuntime.active(entry));
        assertTrue(AgentAirshowStateRuntime.trailDue(entry, 100L, 100L));

        AgentAirshowStateRuntime.markTrail(entry, 150L);

        assertFalse(AgentAirshowStateRuntime.trailDue(entry, 249L, 100L));
        assertTrue(AgentAirshowStateRuntime.trailDue(entry, 250L, 100L));

        AgentAirshowStateRuntime.stop(entry);

        assertFalse(AgentAirshowStateRuntime.active(entry));
        assertTrue(AgentAirshowStateRuntime.trailDue(entry, 100L, 100L));
    }

    @Test
    void adaptsScriptedMovementFrame() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentAirshowStateRuntime.applyFrame(entry, new Point(120, 340), 2000, -1200, -1, true, true);

        assertEquals(2000, AgentMovementStateRuntime.movementVelocityX(entry));
        assertEquals(-1200, AgentMovementStateRuntime.movementVelocityY(entry));
        assertEquals(-1, AgentMovementStateRuntime.facingDirection(entry));
        assertTrue(AgentMovementStateRuntime.inAir(entry));
        assertTrue(AgentClimbStateRuntime.climbing(entry));
    }
}
