package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMovementInputStateTest {
    @Test
    void defaultsMatchLegacyAgentRuntimeEntryFields() {
        AgentMovementInputState state = new AgentMovementInputState();

        assertEquals(0, state.moveDirection());
        assertEquals(0, state.velocityX());
        assertEquals(0, state.velocityY());
        assertFalse(state.hasVelocity());
        assertEquals(1, state.facingDirection());
        assertEquals(1, state.facingDirectionSign());
        assertFalse(state.crouching());
        assertFalse(state.wasMovingX());
    }

    @Test
    void normalizesMovementDirectionAndFacing() {
        AgentMovementInputState state = new AgentMovementInputState();

        state.setMoveDirection(-3);
        state.setFacingDirection(-10);

        assertEquals(-1, state.moveDirection());
        assertEquals(-1, state.facingDirection());
        assertEquals(-1, state.facingDirectionSign());

        state.setMoveDirection(0);
        state.setFacingDirection(0);

        assertEquals(0, state.moveDirection());
        assertEquals(1, state.facingDirection());
        assertEquals(1, state.facingDirectionSign());

        state.setMoveDirection(7);
        assertEquals(1, state.moveDirection());

        state.clearMoveDirection();
        assertEquals(0, state.moveDirection());
    }

    @Test
    void movementVelocityUpdatesFacingWhenMovingHorizontally() {
        AgentMovementInputState state = new AgentMovementInputState();

        state.setVelocity(-120, 4);

        assertEquals(-120, state.velocityX());
        assertEquals(4, state.velocityY());
        assertTrue(state.hasVelocity());
        assertEquals(-1, state.facingDirection());

        state.setVelocity(0, 0);

        assertEquals(0, state.velocityX());
        assertEquals(0, state.velocityY());
        assertFalse(state.hasVelocity());
        assertEquals(-1, state.facingDirection());
    }

    @Test
    void storesCrouchAndHorizontalHysteresis() {
        AgentMovementInputState state = new AgentMovementInputState();

        state.setCrouching(true);
        state.setWasMovingX(true);

        assertTrue(state.crouching());
        assertTrue(state.wasMovingX());

        state.setCrouching(false);
        state.setWasMovingX(false);

        assertFalse(state.crouching());
        assertFalse(state.wasMovingX());
    }
}
