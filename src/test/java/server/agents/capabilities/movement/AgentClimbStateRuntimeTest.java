package server.agents.capabilities.movement;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.maps.Rope;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentClimbStateRuntimeTest {
    @Test
    void adaptsClimbRopeAndDirectionState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Rope rope = new Rope(100, 20, 200, false);

        assertFalse(AgentClimbStateRuntime.climbing(entry));
        assertFalse(AgentClimbStateRuntime.hasClimbRope(entry));
        assertNull(AgentClimbStateRuntime.climbRope(entry));
        assertFalse(AgentClimbStateRuntime.hasClimbVerticalDirection(entry));

        AgentClimbStateRuntime.setClimbingOnRope(entry, rope);
        AgentClimbStateRuntime.setClimbVerticalDirection(entry, -5);

        assertTrue(AgentClimbStateRuntime.climbing(entry));
        assertTrue(AgentClimbStateRuntime.hasClimbRope(entry));
        assertSame(rope, AgentClimbStateRuntime.climbRope(entry));
        assertEquals(-1, AgentClimbStateRuntime.climbVerticalDirection(entry));
        assertTrue(AgentClimbStateRuntime.hasClimbVerticalDirection(entry));

        AgentClimbStateRuntime.setClimbVerticalDirection(entry, 0);

        assertFalse(AgentClimbStateRuntime.hasClimbVerticalDirection(entry));

        assertFalse(AgentClimbStateRuntime.climbUpIntent(entry));
        AgentClimbStateRuntime.setClimbUpIntent(entry, true);
        assertTrue(AgentClimbStateRuntime.climbUpIntent(entry));
        AgentClimbStateRuntime.setClimbUpIntent(entry, false);
        assertFalse(AgentClimbStateRuntime.climbUpIntent(entry));

        assertEquals(0, AgentClimbStateRuntime.ropeGrabCooldownMs(entry));
        AgentClimbStateRuntime.setRopeGrabCooldownMs(entry, 250);
        assertEquals(250, AgentClimbStateRuntime.ropeGrabCooldownMs(entry));
    }

    @Test
    void adaptsRopeEntryIntentState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Rope rope = new Rope(100, 20, 200, false);

        assertFalse(AgentClimbStateRuntime.ropeEntryPending(entry));
        assertNull(AgentClimbStateRuntime.ropeEntryRope(entry));
        assertEquals(0, AgentClimbStateRuntime.ropeEntryY(entry));

        AgentClimbStateRuntime.queueRopeEntry(entry, rope, 90);

        assertTrue(AgentClimbStateRuntime.ropeEntryPending(entry));
        assertSame(rope, AgentClimbStateRuntime.ropeEntryRope(entry));
        assertEquals(90, AgentClimbStateRuntime.ropeEntryY(entry));

        AgentClimbStateRuntime.clearRopeEntry(entry);

        assertFalse(AgentClimbStateRuntime.ropeEntryPending(entry));
        assertNull(AgentClimbStateRuntime.ropeEntryRope(entry));
        assertEquals(0, AgentClimbStateRuntime.ropeEntryY(entry));
    }

    @Test
    void adaptsBlockedRopeGrabState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Rope rope = new Rope(100, 20, 200, false);

        assertNull(AgentClimbStateRuntime.blockedRopeGrab(entry));

        AgentClimbStateRuntime.setBlockedRopeGrab(entry, rope);

        assertSame(rope, AgentClimbStateRuntime.blockedRopeGrab(entry));

        AgentClimbStateRuntime.clearBlockedRopeGrab(entry);

        assertNull(AgentClimbStateRuntime.blockedRopeGrab(entry));
    }
}
