package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotAirshowStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotAirshowStateRuntimeTest {
    @Test
    void adaptsAirshowLifecycleAndTrailTiming() {
        BotEntry entry = new BotEntry(null, null, null);

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
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotAirshowStateRuntime.applyFrame(entry, new Point(120, 340), 2000, -1200, -1, true, true);

        assertEquals(2000, entry.movementVelX());
        assertEquals(-1200, entry.movementVelY());
        assertEquals(-1, entry.facingDirection());
        assertTrue(entry.inAir());
        assertTrue(entry.climbing());
    }
}
