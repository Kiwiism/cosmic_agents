package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.maps.Rope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotClimbStateRuntimeTest {
    @Test
    void adaptsClimbRopeAndDirectionState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Rope rope = new Rope(100, 20, 200, false);

        assertFalse(AgentBotClimbStateRuntime.climbing(entry));
        assertFalse(AgentBotClimbStateRuntime.hasClimbRope(entry));
        assertNull(AgentBotClimbStateRuntime.climbRope(entry));
        assertFalse(AgentBotClimbStateRuntime.hasClimbVerticalDirection(entry));

        AgentBotClimbStateRuntime.setClimbingOnRope(entry, rope);
        AgentBotClimbStateRuntime.setClimbVerticalDirection(entry, -5);

        assertTrue(AgentBotClimbStateRuntime.climbing(entry));
        assertTrue(AgentBotClimbStateRuntime.hasClimbRope(entry));
        assertSame(rope, AgentBotClimbStateRuntime.climbRope(entry));
        assertEquals(-1, AgentBotClimbStateRuntime.climbVerticalDirection(entry));
        assertTrue(AgentBotClimbStateRuntime.hasClimbVerticalDirection(entry));

        AgentBotClimbStateRuntime.setClimbVerticalDirection(entry, 0);

        assertFalse(AgentBotClimbStateRuntime.hasClimbVerticalDirection(entry));

        assertFalse(AgentBotClimbStateRuntime.climbUpIntent(entry));
        AgentBotClimbStateRuntime.setClimbUpIntent(entry, true);
        assertTrue(AgentBotClimbStateRuntime.climbUpIntent(entry));
        AgentBotClimbStateRuntime.setClimbUpIntent(entry, false);
        assertFalse(AgentBotClimbStateRuntime.climbUpIntent(entry));

        assertEquals(0, AgentBotClimbStateRuntime.ropeGrabCooldownMs(entry));
        AgentBotClimbStateRuntime.setRopeGrabCooldownMs(entry, 250);
        assertEquals(250, AgentBotClimbStateRuntime.ropeGrabCooldownMs(entry));
    }

    @Test
    void adaptsRopeEntryIntentState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Rope rope = new Rope(100, 20, 200, false);

        assertFalse(AgentBotClimbStateRuntime.ropeEntryPending(entry));
        assertNull(AgentBotClimbStateRuntime.ropeEntryRope(entry));
        assertEquals(0, AgentBotClimbStateRuntime.ropeEntryY(entry));

        AgentBotClimbStateRuntime.queueRopeEntry(entry, rope, 90);

        assertTrue(AgentBotClimbStateRuntime.ropeEntryPending(entry));
        assertSame(rope, AgentBotClimbStateRuntime.ropeEntryRope(entry));
        assertEquals(90, AgentBotClimbStateRuntime.ropeEntryY(entry));

        AgentBotClimbStateRuntime.clearRopeEntry(entry);

        assertFalse(AgentBotClimbStateRuntime.ropeEntryPending(entry));
        assertNull(AgentBotClimbStateRuntime.ropeEntryRope(entry));
        assertEquals(0, AgentBotClimbStateRuntime.ropeEntryY(entry));
    }

    @Test
    void adaptsBlockedRopeGrabState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Rope rope = new Rope(100, 20, 200, false);

        assertNull(AgentBotClimbStateRuntime.blockedRopeGrab(entry));

        AgentBotClimbStateRuntime.setBlockedRopeGrab(entry, rope);

        assertSame(rope, AgentBotClimbStateRuntime.blockedRopeGrab(entry));

        AgentBotClimbStateRuntime.clearBlockedRopeGrab(entry);

        assertNull(AgentBotClimbStateRuntime.blockedRopeGrab(entry));
    }
}
