package server.agents.capabilities.movement.fidget;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFidgetStateRuntimeTest {
    @Test
    void adaptsFidgetStartClearAndPointCopies() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Point origin = new Point(10, 20);

        AgentFidgetStateRuntime.start(
                entry,
                AgentFidgetMode.DIAGONAL_JUMP,
                AgentFidgetTrigger.SOCIAL,
                5_000L,
                1_000L,
                -1,
                true,
                200,
                origin,
                1_600L,
                7_000L);
        origin.x = 999;

        assertTrue(AgentFidgetStateRuntime.active(entry));
        assertFalse(AgentFidgetStateRuntime.inactive(entry));
        assertEquals(AgentFidgetMode.DIAGONAL_JUMP, AgentFidgetStateRuntime.mode(entry));
        assertTrue(AgentFidgetStateRuntime.modeIs(entry, AgentFidgetMode.DIAGONAL_JUMP));
        assertTrue(AgentFidgetStateRuntime.modeIsAny(entry, AgentFidgetMode.JUMP, AgentFidgetMode.DIAGONAL_JUMP));
        assertEquals(AgentFidgetTrigger.SOCIAL, AgentFidgetStateRuntime.trigger(entry));
        assertEquals(5_000L, AgentFidgetStateRuntime.untilMs(entry));
        assertEquals(1_000L, AgentFidgetStateRuntime.nextActionAtMs(entry));
        assertEquals(-1, AgentFidgetStateRuntime.airSteerDir(entry));
        assertEquals(-1, AgentFidgetStateRuntime.jumpDir(entry));
        assertEquals(-1, AgentFidgetStateRuntime.moveDir(entry));
        assertTrue(AgentFidgetStateRuntime.spamAirSteer(entry));
        assertEquals(200, AgentFidgetStateRuntime.actionBaseDelayMs(entry));
        assertEquals(1_000L, AgentFidgetStateRuntime.nextJumpAtMs(entry));
        assertEquals(new Point(10, 20), AgentFidgetStateRuntime.originPos(entry));
        assertEquals(1_600L, AgentFidgetStateRuntime.nextVisualAtMs(entry));
        assertEquals(7_000L, AgentFidgetStateRuntime.nextFidgetAtMs(entry));

        Point returned = AgentFidgetStateRuntime.originPos(entry);
        returned.y = 777;
        assertEquals(new Point(10, 20), AgentFidgetStateRuntime.originPos(entry));

        AgentFidgetStateRuntime.clear(entry);

        assertFalse(AgentFidgetStateRuntime.active(entry));
        assertTrue(AgentFidgetStateRuntime.inactive(entry));
        assertEquals(AgentFidgetMode.NONE, AgentFidgetStateRuntime.mode(entry));
        assertEquals(AgentFidgetTrigger.NONE, AgentFidgetStateRuntime.trigger(entry));
        assertEquals(0L, AgentFidgetStateRuntime.untilMs(entry));
        assertEquals(0L, AgentFidgetStateRuntime.nextActionAtMs(entry));
        assertEquals(0, AgentFidgetStateRuntime.airSteerDir(entry));
        assertEquals(0, AgentFidgetStateRuntime.jumpDir(entry));
        assertEquals(0, AgentFidgetStateRuntime.moveDir(entry));
        assertFalse(AgentFidgetStateRuntime.spamAirSteer(entry));
        assertEquals(0, AgentFidgetStateRuntime.actionBaseDelayMs(entry));
        assertEquals(0L, AgentFidgetStateRuntime.nextJumpAtMs(entry));
        assertNull(AgentFidgetStateRuntime.originPos(entry));
        assertEquals(0L, AgentFidgetStateRuntime.nextVisualAtMs(entry));
    }

    @Test
    void adaptsFidgetDueChecksAndMutableProgress() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentFidgetStateRuntime.start(
                entry,
                AgentFidgetMode.SPAM_SIDEWAYS,
                AgentFidgetTrigger.IDLE,
                5_000L,
                1_000L,
                1,
                false,
                150,
                null,
                1_400L,
                2_000L);

        assertFalse(AgentFidgetStateRuntime.expired(entry, 4_999L));
        assertTrue(AgentFidgetStateRuntime.expired(entry, 5_000L));
        assertFalse(AgentFidgetStateRuntime.actionDue(entry, 999L));
        assertTrue(AgentFidgetStateRuntime.actionDue(entry, 1_000L));
        assertFalse(AgentFidgetStateRuntime.jumpDue(entry, 999L));
        assertTrue(AgentFidgetStateRuntime.jumpDue(entry, 1_000L));
        assertFalse(AgentFidgetStateRuntime.visualDue(entry, 1_399L));
        assertTrue(AgentFidgetStateRuntime.visualDue(entry, 1_400L));
        assertFalse(AgentFidgetStateRuntime.nextFidgetDue(entry, 1_999L));
        assertTrue(AgentFidgetStateRuntime.nextFidgetDue(entry, 2_000L));
        assertTrue(AgentFidgetStateRuntime.idleRollNotScheduled(entry));

        AgentFidgetStateRuntime.setAirSteerDir(entry, -1);
        AgentFidgetStateRuntime.setJumpDir(entry, 1);
        AgentFidgetStateRuntime.setMoveDir(entry, -1);
        AgentFidgetStateRuntime.setNextActionAtMs(entry, 2_500L);
        AgentFidgetStateRuntime.setNextJumpAtMs(entry, 2_600L);
        AgentFidgetStateRuntime.setNextVisualAtMs(entry, 2_700L);
        AgentFidgetStateRuntime.setNextFidgetAtMs(entry, 2_800L);
        AgentFidgetStateRuntime.setNextIdleRollAtMs(entry, 3_000L);

        assertEquals(-1, AgentFidgetStateRuntime.airSteerDir(entry));
        assertEquals(1, AgentFidgetStateRuntime.jumpDir(entry));
        assertEquals(-1, AgentFidgetStateRuntime.moveDir(entry));
        assertEquals(2_500L, AgentFidgetStateRuntime.nextActionAtMs(entry));
        assertEquals(2_600L, AgentFidgetStateRuntime.nextJumpAtMs(entry));
        assertEquals(2_700L, AgentFidgetStateRuntime.nextVisualAtMs(entry));
        assertEquals(2_800L, AgentFidgetStateRuntime.nextFidgetAtMs(entry));
        assertEquals(3_000L, AgentFidgetStateRuntime.nextIdleRollAtMs(entry));
        assertFalse(AgentFidgetStateRuntime.idleRollNotScheduled(entry));
        assertFalse(AgentFidgetStateRuntime.idleRollDue(entry, 2_999L));
        assertTrue(AgentFidgetStateRuntime.idleRollDue(entry, 3_000L));
    }
}
