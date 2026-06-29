package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentGroundMovementPolicyTest {
    @Test
    void shouldStopInsideStopDistance() {
        assertEquals(0, AgentGroundMovementPolicy.calcStepX(0, 20, true, 25, 80, 12));
        assertEquals(0, AgentGroundMovementPolicy.calcStepX(0, -20, true, 25, 80, 12));
    }

    @Test
    void shouldHoldUntilFollowDistanceWhenNotAlreadyMoving() {
        assertEquals(0, AgentGroundMovementPolicy.calcStepX(0, 80, false, 25, 80, 12));
        assertEquals(12, AgentGroundMovementPolicy.calcStepX(0, 81, false, 25, 80, 12));
    }

    @Test
    void shouldKeepMovingInsideFollowDistanceWhenAlreadyMoving() {
        assertEquals(12, AgentGroundMovementPolicy.calcStepX(0, 80, true, 25, 80, 12));
        assertEquals(-12, AgentGroundMovementPolicy.calcStepX(0, -80, true, 25, 80, 12));
    }

    @Test
    void shouldClampStepToRemainingDistance() {
        assertEquals(31, AgentGroundMovementPolicy.calcStepX(0, 31, true, 25, 80, 40));
        assertEquals(-31, AgentGroundMovementPolicy.calcStepX(0, -31, true, 25, 80, 40));
    }

    @Test
    void shouldUseFullWalkStepForDistantTargets() {
        assertEquals(12, AgentGroundMovementPolicy.calcStepX(0, 200, false, 25, 80, 12));
        assertEquals(-12, AgentGroundMovementPolicy.calcStepX(0, -200, false, 25, 80, 12));
    }
}
