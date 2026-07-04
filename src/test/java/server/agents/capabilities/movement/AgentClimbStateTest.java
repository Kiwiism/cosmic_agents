package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.maps.Rope;

import static org.junit.jupiter.api.Assertions.*;

class AgentClimbStateTest {
    @Test
    void defaultsToIdleClimbState() {
        AgentClimbState state = new AgentClimbState();

        assertFalse(state.climbing());
        assertNull(state.climbRope());
        assertFalse(state.hasClimbRope());
        assertEquals(0, state.verticalDirection());
        assertFalse(state.hasVerticalDirection());
        assertFalse(state.climbUpIntent());
        assertNull(state.blockedRopeGrab());
        assertEquals(0, state.ropeGrabCooldownMs());
        assertFalse(state.ropeEntryPending());
        assertNull(state.ropeEntryRope());
        assertEquals(0, state.ropeEntryY());
    }

    @Test
    void setClimbingOnRopeTracksAttachedRope() {
        AgentClimbState state = new AgentClimbState();
        Rope rope = new Rope(100, 20, 200, false);

        state.setClimbingOnRope(rope);

        assertTrue(state.climbing());
        assertSame(rope, state.climbRope());
        assertTrue(state.hasClimbRope());

        state.setClimbingOnRope(null);

        assertFalse(state.climbing());
        assertNull(state.climbRope());
        assertFalse(state.hasClimbRope());
    }

    @Test
    void scriptedClimbingFlagPreservesLegacyRopeReference() {
        AgentClimbState state = new AgentClimbState();
        Rope rope = new Rope(100, 20, 200, false);
        state.setClimbingOnRope(rope);

        state.setClimbingFlag(false);

        assertFalse(state.climbing());
        assertSame(rope, state.climbRope());

        state.setClimbingFlag(true);

        assertTrue(state.climbing());
        assertSame(rope, state.climbRope());
    }

    @Test
    void normalizesVerticalDirection() {
        AgentClimbState state = new AgentClimbState();

        state.setVerticalDirection(-99);
        assertEquals(-1, state.verticalDirection());
        assertTrue(state.hasVerticalDirection());

        state.setVerticalDirection(99);
        assertEquals(1, state.verticalDirection());

        state.setVerticalDirection(0);
        assertEquals(0, state.verticalDirection());
        assertFalse(state.hasVerticalDirection());
    }

    @Test
    void tracksTransientClimbControls() {
        AgentClimbState state = new AgentClimbState();
        Rope rope = new Rope(100, 20, 200, false);

        state.setClimbUpIntent(true);
        state.setBlockedRopeGrab(rope);
        state.setRopeGrabCooldownMs(250);

        assertTrue(state.climbUpIntent());
        assertSame(rope, state.blockedRopeGrab());
        assertEquals(250, state.ropeGrabCooldownMs());

        state.setClimbUpIntent(false);
        state.clearBlockedRopeGrab();

        assertFalse(state.climbUpIntent());
        assertNull(state.blockedRopeGrab());
    }

    @Test
    void queuesAndClearsRopeEntry() {
        AgentClimbState state = new AgentClimbState();
        Rope rope = new Rope(100, 20, 200, false);

        state.queueRopeEntry(rope, 90);

        assertTrue(state.ropeEntryPending());
        assertSame(rope, state.ropeEntryRope());
        assertEquals(90, state.ropeEntryY());

        state.clearRopeEntry();

        assertFalse(state.ropeEntryPending());
        assertNull(state.ropeEntryRope());
        assertEquals(0, state.ropeEntryY());
    }
}
