package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import server.agents.integration.AgentBotClimbStateRuntime;

import server.agents.integration.AgentBotMovementStateRuntime;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotAirshowStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotAirshowStateRuntimeTest {
    @Test
    void adaptsAirshowLifecycleAndTrailTiming() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentBotAirshowStateRuntime.active(entry));

        AgentBotAirshowStateRuntime.start(entry);

        assertTrue(AgentBotAirshowStateRuntime.active(entry));
        assertTrue(AgentBotAirshowStateRuntime.trailDue(entry, 100L, 100L));

        AgentBotAirshowStateRuntime.markTrail(entry, 150L);

        assertFalse(AgentBotAirshowStateRuntime.trailDue(entry, 249L, 100L));
        assertTrue(AgentBotAirshowStateRuntime.trailDue(entry, 250L, 100L));

        AgentBotAirshowStateRuntime.stop(entry);

        assertFalse(AgentBotAirshowStateRuntime.active(entry));
        assertTrue(AgentBotAirshowStateRuntime.trailDue(entry, 100L, 100L));
    }

    @Test
    void adaptsScriptedMovementFrame() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentBotAirshowStateRuntime.applyFrame(entry, new Point(120, 340), 2000, -1200, -1, true, true);

        assertEquals(2000, AgentBotMovementStateRuntime.movementVelocityX(entry));
        assertEquals(-1200, AgentBotMovementStateRuntime.movementVelocityY(entry));
        assertEquals(-1, AgentBotMovementStateRuntime.facingDirection(entry));
        assertTrue(AgentBotMovementStateRuntime.inAir(entry));
        assertTrue(AgentBotClimbStateRuntime.climbing(entry));
    }
}
