package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentMovementStuckStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMovementStuckStateRuntimeTest {
    @Test
    void adaptsStuckProgressAndCooldownState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(0, AgentMovementStuckStateRuntime.stuckMs(entry));
        assertFalse(AgentMovementStuckStateRuntime.hasUnstuckCooldown(entry));
        assertFalse(AgentMovementStuckStateRuntime.hasStuckCheckPosition(entry));

        AgentMovementStuckStateRuntime.addStuckMs(entry, 250);
        AgentMovementStuckStateRuntime.setUnstuckCooldownMs(entry, 5000);
        AgentMovementStuckStateRuntime.rememberStuckCheckPosition(entry, new Point(10, 20));

        assertEquals(250, AgentMovementStuckStateRuntime.stuckMs(entry));
        assertEquals(5000, AgentMovementStuckStateRuntime.unstuckCooldownMs(entry));
        assertTrue(AgentMovementStuckStateRuntime.hasUnstuckCooldown(entry));
        assertTrue(AgentMovementStuckStateRuntime.hasStuckCheckPosition(entry));
        assertFalse(AgentMovementStuckStateRuntime.movedSinceStuckCheck(entry, new Point(18, 28), 8));
        assertTrue(AgentMovementStuckStateRuntime.movedSinceStuckCheck(entry, new Point(19, 28), 8));

        AgentMovementStuckStateRuntime.addStuckMs(entry, 250);

        assertTrue(AgentMovementStuckStateRuntime.stuckForAtLeast(entry, 500));

        AgentMovementStuckStateRuntime.resetStuckProgress(entry);

        assertEquals(0, AgentMovementStuckStateRuntime.stuckMs(entry));
        assertFalse(AgentMovementStuckStateRuntime.hasStuckCheckPosition(entry));
        assertEquals(5000, AgentMovementStuckStateRuntime.unstuckCooldownMs(entry));
    }
}
