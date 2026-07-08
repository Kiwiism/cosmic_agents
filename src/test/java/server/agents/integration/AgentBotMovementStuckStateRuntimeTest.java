package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMovementStuckStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotMovementStuckStateRuntimeTest {
    @Test
    void adaptsStuckProgressAndCooldownState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(0, AgentBotMovementStuckStateRuntime.stuckMs(entry));
        assertFalse(AgentBotMovementStuckStateRuntime.hasUnstuckCooldown(entry));
        assertFalse(AgentBotMovementStuckStateRuntime.hasStuckCheckPosition(entry));

        AgentBotMovementStuckStateRuntime.addStuckMs(entry, 250);
        AgentBotMovementStuckStateRuntime.setUnstuckCooldownMs(entry, 5000);
        AgentBotMovementStuckStateRuntime.rememberStuckCheckPosition(entry, new Point(10, 20));

        assertEquals(250, AgentBotMovementStuckStateRuntime.stuckMs(entry));
        assertEquals(5000, AgentBotMovementStuckStateRuntime.unstuckCooldownMs(entry));
        assertTrue(AgentBotMovementStuckStateRuntime.hasUnstuckCooldown(entry));
        assertTrue(AgentBotMovementStuckStateRuntime.hasStuckCheckPosition(entry));
        assertFalse(AgentBotMovementStuckStateRuntime.movedSinceStuckCheck(entry, new Point(18, 28), 8));
        assertTrue(AgentBotMovementStuckStateRuntime.movedSinceStuckCheck(entry, new Point(19, 28), 8));

        AgentBotMovementStuckStateRuntime.addStuckMs(entry, 250);

        assertTrue(AgentBotMovementStuckStateRuntime.stuckForAtLeast(entry, 500));

        AgentBotMovementStuckStateRuntime.resetStuckProgress(entry);

        assertEquals(0, AgentBotMovementStuckStateRuntime.stuckMs(entry));
        assertFalse(AgentBotMovementStuckStateRuntime.hasStuckCheckPosition(entry));
        assertEquals(5000, AgentBotMovementStuckStateRuntime.unstuckCooldownMs(entry));
    }
}
