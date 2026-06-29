package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentMovementTimingPolicyTest {
    @Test
    void tickDownClampsAtZeroAfterOneTick() {
        assertEquals(0, AgentMovementTimingPolicy.tickDown(0, 100));
        assertEquals(0, AgentMovementTimingPolicy.tickDown(-1, 100));
        assertEquals(150, AgentMovementTimingPolicy.tickDown(250, 100));
        assertEquals(0, AgentMovementTimingPolicy.tickDown(50, 100));
    }

    @Test
    void delayAfterCurrentTickClampsAtZeroAfterOneTick() {
        assertEquals(0, AgentMovementTimingPolicy.delayAfterCurrentTick(0, 100));
        assertEquals(0, AgentMovementTimingPolicy.delayAfterCurrentTick(-1, 100));
        assertEquals(900, AgentMovementTimingPolicy.delayAfterCurrentTick(1000, 100));
        assertEquals(0, AgentMovementTimingPolicy.delayAfterCurrentTick(50, 100));
    }

    @Test
    void negativeTickDurationPreservesLegacyNonIncreasingDelay() {
        assertEquals(250, AgentMovementTimingPolicy.tickDown(250, -100));
        assertEquals(1000, AgentMovementTimingPolicy.delayAfterCurrentTick(1000, -100));
    }
}
