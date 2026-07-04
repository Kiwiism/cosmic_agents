package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.*;

class AgentMovementPhysicsStateTest {
    @Test
    void defaultsToGroundedIdlePhysics() {
        AgentMovementPhysicsState state = new AgentMovementPhysicsState();

        assertFalse(state.inAir());
        assertEquals(0f, state.verticalVelocity());
        assertEquals(0.0, state.horizontalSpeed());
        assertEquals(0.0, state.physicsX());
        assertEquals(0.0, state.physicsY());
        assertEquals(0.0, state.groundCarryMs());
        assertFalse(state.hasFallPeakPhysicsY());
        assertEquals(0, state.jumpCooldownMs());
    }

    @Test
    void storesPositionVelocityAndGroundCarry() {
        AgentMovementPhysicsState state = new AgentMovementPhysicsState();

        state.setInAir(true);
        state.setVerticalVelocity(-12.5f);
        state.setHorizontalSpeed(3.25);
        state.setPhysicsPosition(10.5, 20.5);
        state.addPhysicsPosition(2.0, -3.0);
        state.setGroundCarryMs(6.75);

        assertTrue(state.inAir());
        assertEquals(-12.5f, state.verticalVelocity());
        assertEquals(3.25, state.horizontalSpeed());
        assertEquals(12.5, state.physicsX());
        assertEquals(17.5, state.physicsY());
        assertEquals(6.75, state.groundCarryMs());
        assertEquals(new AgentGroundTravelState(12.5, 3.25, 6.75), state.groundTravelState());
    }

    @Test
    void copiesPointPositionWhenPresent() {
        AgentMovementPhysicsState state = new AgentMovementPhysicsState();

        state.setPhysicsPosition(new Point(10, 20));
        state.setPhysicsPosition(null);

        assertEquals(10.0, state.physicsX());
        assertEquals(20.0, state.physicsY());
    }

    @Test
    void fallPeakTracksHighestAirPointAndResets() {
        AgentMovementPhysicsState state = new AgentMovementPhysicsState();

        state.setFallPeakPhysicsY(50.0);
        state.recordFallPeakPhysicsY(60.0);
        state.recordFallPeakPhysicsY(40.0);

        assertTrue(state.hasFallPeakPhysicsY());
        assertEquals(40.0, state.fallPeakPhysicsY());

        state.resetFallPeakPhysicsY();

        assertFalse(state.hasFallPeakPhysicsY());
    }

    @Test
    void storesAndClearsJumpCooldown() {
        AgentMovementPhysicsState state = new AgentMovementPhysicsState();

        state.setJumpCooldownMs(120);
        assertEquals(120, state.jumpCooldownMs());

        state.clearJumpCooldown();
        assertEquals(0, state.jumpCooldownMs());
    }
}
