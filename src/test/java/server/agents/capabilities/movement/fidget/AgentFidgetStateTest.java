package server.agents.capabilities.movement.fidget;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.*;

class AgentFidgetStateTest {
    @Test
    void defaultsToInactiveState() {
        AgentFidgetState state = new AgentFidgetState();

        assertEquals(AgentFidgetMode.NONE, state.mode());
        assertFalse(state.active());
        assertEquals(AgentFidgetTrigger.NONE, state.trigger());
        assertEquals(0L, state.untilMs());
        assertEquals(0L, state.nextActionAtMs());
        assertEquals(0L, state.nextFidgetAtMs());
        assertEquals(0L, state.nextIdleRollAtMs());
        assertEquals(0, state.airSteerDir());
        assertEquals(0, state.jumpDir());
        assertEquals(0, state.moveDir());
        assertFalse(state.spamAirSteer());
        assertEquals(0, state.actionBaseDelayMs());
        assertEquals(0L, state.nextJumpAtMs());
        assertNull(state.originPos());
        assertEquals(0L, state.nextVisualAtMs());
    }

    @Test
    void startPreservesLegacyDerivedDirectionsAndCopiesOrigin() {
        AgentFidgetState state = new AgentFidgetState();
        Point origin = new Point(10, 20);

        state.start(AgentFidgetMode.DIAGONAL_JUMP, AgentFidgetTrigger.SOCIAL,
                5_000L, 1_000L, -1, true, 200, origin, 1_600L, 7_000L);
        origin.x = 999;

        assertTrue(state.active());
        assertEquals(AgentFidgetMode.DIAGONAL_JUMP, state.mode());
        assertEquals(AgentFidgetTrigger.SOCIAL, state.trigger());
        assertEquals(5_000L, state.untilMs());
        assertEquals(1_000L, state.nextActionAtMs());
        assertEquals(-1, state.airSteerDir());
        assertEquals(-1, state.jumpDir());
        assertEquals(-1, state.moveDir());
        assertTrue(state.spamAirSteer());
        assertEquals(200, state.actionBaseDelayMs());
        assertEquals(1_000L, state.nextJumpAtMs());
        assertEquals(new Point(10, 20), state.originPos());
        assertEquals(1_600L, state.nextVisualAtMs());
        assertEquals(7_000L, state.nextFidgetAtMs());

        Point returned = state.originPos();
        returned.y = 777;
        assertEquals(new Point(10, 20), state.originPos());
    }

    @Test
    void zeroAirSteerStartsJumpDirectionRightLikeLegacy() {
        AgentFidgetState state = new AgentFidgetState();

        state.start(AgentFidgetMode.JUMP, AgentFidgetTrigger.IDLE,
                5_000L, 1_000L, 0, false, 150, null, 1_400L, 2_000L);

        assertEquals(0, state.airSteerDir());
        assertEquals(1, state.jumpDir());
        assertEquals(0, state.moveDir());
    }

    @Test
    void clearResetsOnlyActiveFidgetFields() {
        AgentFidgetState state = new AgentFidgetState();
        state.start(AgentFidgetMode.SPAM_SIDEWAYS, AgentFidgetTrigger.IDLE,
                5_000L, 1_000L, 1, true, 150, new Point(10, 20), 1_400L, 2_000L);
        state.setNextIdleRollAtMs(3_000L);
        state.clearActiveState();

        assertEquals(AgentFidgetMode.NONE, state.mode());
        assertEquals(AgentFidgetTrigger.NONE, state.trigger());
        assertEquals(0L, state.untilMs());
        assertEquals(0L, state.nextActionAtMs());
        assertEquals(0, state.airSteerDir());
        assertEquals(0, state.jumpDir());
        assertEquals(0, state.moveDir());
        assertFalse(state.spamAirSteer());
        assertEquals(0, state.actionBaseDelayMs());
        assertEquals(0L, state.nextJumpAtMs());
        assertNull(state.originPos());
        assertEquals(0L, state.nextVisualAtMs());
        assertEquals(2_000L, state.nextFidgetAtMs());
        assertEquals(3_000L, state.nextIdleRollAtMs());
    }

    @Test
    void mutableProgressSettersUpdateRuntimeState() {
        AgentFidgetState state = new AgentFidgetState();

        state.setMode(AgentFidgetMode.PRONE);
        state.setAirSteerDir(-1);
        state.setJumpDir(1);
        state.setMoveDir(-1);
        state.setSpamAirSteer(true);
        state.setActionBaseDelayMs(100);
        state.setUntilMs(5_000L);
        state.setNextActionAtMs(2_500L);
        state.setNextJumpAtMs(2_600L);
        state.setNextVisualAtMs(2_700L);
        state.setNextFidgetAtMs(2_800L);
        state.setNextIdleRollAtMs(3_000L);

        assertEquals(AgentFidgetMode.PRONE, state.mode());
        assertEquals(-1, state.airSteerDir());
        assertEquals(1, state.jumpDir());
        assertEquals(-1, state.moveDir());
        assertTrue(state.spamAirSteer());
        assertEquals(100, state.actionBaseDelayMs());
        assertEquals(5_000L, state.untilMs());
        assertEquals(2_500L, state.nextActionAtMs());
        assertEquals(2_600L, state.nextJumpAtMs());
        assertEquals(2_700L, state.nextVisualAtMs());
        assertEquals(2_800L, state.nextFidgetAtMs());
        assertEquals(3_000L, state.nextIdleRollAtMs());
    }
}
