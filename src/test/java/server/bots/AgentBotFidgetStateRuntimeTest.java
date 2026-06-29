package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.fidget.AgentFidgetMode;
import server.agents.capabilities.movement.fidget.AgentFidgetTrigger;
import server.agents.integration.AgentBotFidgetStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotFidgetStateRuntimeTest {
    @Test
    void adaptsFidgetStartClearAndPointCopies() {
        BotEntry entry = new BotEntry(null, null, null);
        Point origin = new Point(10, 20);

        AgentBotFidgetStateRuntime.start(
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

        assertTrue(AgentBotFidgetStateRuntime.active(entry));
        assertFalse(AgentBotFidgetStateRuntime.inactive(entry));
        assertEquals(AgentFidgetMode.DIAGONAL_JUMP, AgentBotFidgetStateRuntime.mode(entry));
        assertTrue(AgentBotFidgetStateRuntime.modeIs(entry, AgentFidgetMode.DIAGONAL_JUMP));
        assertTrue(AgentBotFidgetStateRuntime.modeIsAny(entry, AgentFidgetMode.JUMP, AgentFidgetMode.DIAGONAL_JUMP));
        assertEquals(AgentFidgetTrigger.SOCIAL, AgentBotFidgetStateRuntime.trigger(entry));
        assertEquals(5_000L, AgentBotFidgetStateRuntime.untilMs(entry));
        assertEquals(1_000L, AgentBotFidgetStateRuntime.nextActionAtMs(entry));
        assertEquals(-1, AgentBotFidgetStateRuntime.airSteerDir(entry));
        assertEquals(-1, AgentBotFidgetStateRuntime.jumpDir(entry));
        assertEquals(-1, AgentBotFidgetStateRuntime.moveDir(entry));
        assertTrue(AgentBotFidgetStateRuntime.spamAirSteer(entry));
        assertEquals(200, AgentBotFidgetStateRuntime.actionBaseDelayMs(entry));
        assertEquals(1_000L, AgentBotFidgetStateRuntime.nextJumpAtMs(entry));
        assertEquals(new Point(10, 20), AgentBotFidgetStateRuntime.originPos(entry));
        assertEquals(1_600L, AgentBotFidgetStateRuntime.nextVisualAtMs(entry));
        assertEquals(7_000L, AgentBotFidgetStateRuntime.nextFidgetAtMs(entry));

        Point returned = AgentBotFidgetStateRuntime.originPos(entry);
        returned.y = 777;
        assertEquals(new Point(10, 20), AgentBotFidgetStateRuntime.originPos(entry));

        AgentBotFidgetStateRuntime.clear(entry);

        assertFalse(AgentBotFidgetStateRuntime.active(entry));
        assertTrue(AgentBotFidgetStateRuntime.inactive(entry));
        assertEquals(AgentFidgetMode.NONE, AgentBotFidgetStateRuntime.mode(entry));
        assertEquals(AgentFidgetTrigger.NONE, AgentBotFidgetStateRuntime.trigger(entry));
        assertEquals(0L, AgentBotFidgetStateRuntime.untilMs(entry));
        assertEquals(0L, AgentBotFidgetStateRuntime.nextActionAtMs(entry));
        assertEquals(0, AgentBotFidgetStateRuntime.airSteerDir(entry));
        assertEquals(0, AgentBotFidgetStateRuntime.jumpDir(entry));
        assertEquals(0, AgentBotFidgetStateRuntime.moveDir(entry));
        assertFalse(AgentBotFidgetStateRuntime.spamAirSteer(entry));
        assertEquals(0, AgentBotFidgetStateRuntime.actionBaseDelayMs(entry));
        assertEquals(0L, AgentBotFidgetStateRuntime.nextJumpAtMs(entry));
        assertNull(AgentBotFidgetStateRuntime.originPos(entry));
        assertEquals(0L, AgentBotFidgetStateRuntime.nextVisualAtMs(entry));
    }

    @Test
    void adaptsFidgetDueChecksAndMutableProgress() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotFidgetStateRuntime.start(
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

        assertFalse(AgentBotFidgetStateRuntime.expired(entry, 4_999L));
        assertTrue(AgentBotFidgetStateRuntime.expired(entry, 5_000L));
        assertFalse(AgentBotFidgetStateRuntime.actionDue(entry, 999L));
        assertTrue(AgentBotFidgetStateRuntime.actionDue(entry, 1_000L));
        assertFalse(AgentBotFidgetStateRuntime.jumpDue(entry, 999L));
        assertTrue(AgentBotFidgetStateRuntime.jumpDue(entry, 1_000L));
        assertFalse(AgentBotFidgetStateRuntime.visualDue(entry, 1_399L));
        assertTrue(AgentBotFidgetStateRuntime.visualDue(entry, 1_400L));
        assertFalse(AgentBotFidgetStateRuntime.nextFidgetDue(entry, 1_999L));
        assertTrue(AgentBotFidgetStateRuntime.nextFidgetDue(entry, 2_000L));
        assertTrue(AgentBotFidgetStateRuntime.idleRollNotScheduled(entry));

        AgentBotFidgetStateRuntime.setAirSteerDir(entry, -1);
        AgentBotFidgetStateRuntime.setJumpDir(entry, 1);
        AgentBotFidgetStateRuntime.setMoveDir(entry, -1);
        AgentBotFidgetStateRuntime.setNextActionAtMs(entry, 2_500L);
        AgentBotFidgetStateRuntime.setNextJumpAtMs(entry, 2_600L);
        AgentBotFidgetStateRuntime.setNextVisualAtMs(entry, 2_700L);
        AgentBotFidgetStateRuntime.setNextFidgetAtMs(entry, 2_800L);
        AgentBotFidgetStateRuntime.setNextIdleRollAtMs(entry, 3_000L);

        assertEquals(-1, AgentBotFidgetStateRuntime.airSteerDir(entry));
        assertEquals(1, AgentBotFidgetStateRuntime.jumpDir(entry));
        assertEquals(-1, AgentBotFidgetStateRuntime.moveDir(entry));
        assertEquals(2_500L, AgentBotFidgetStateRuntime.nextActionAtMs(entry));
        assertEquals(2_600L, AgentBotFidgetStateRuntime.nextJumpAtMs(entry));
        assertEquals(2_700L, AgentBotFidgetStateRuntime.nextVisualAtMs(entry));
        assertEquals(2_800L, AgentBotFidgetStateRuntime.nextFidgetAtMs(entry));
        assertEquals(3_000L, AgentBotFidgetStateRuntime.nextIdleRollAtMs(entry));
        assertFalse(AgentBotFidgetStateRuntime.idleRollNotScheduled(entry));
        assertFalse(AgentBotFidgetStateRuntime.idleRollDue(entry, 2_999L));
        assertTrue(AgentBotFidgetStateRuntime.idleRollDue(entry, 3_000L));
    }
}
