package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMovementStuckStateTest {
    @Test
    void defaultsPreserveLegacyAgentRuntimeEntryValues() {
        AgentMovementStuckState state = new AgentMovementStuckState();

        assertEquals(0, state.stuckMs());
        assertEquals(0, state.unstuckCooldownMs());
        assertEquals(Integer.MIN_VALUE, state.stuckCheckX());
        assertEquals(Integer.MIN_VALUE, state.stuckCheckY());
        assertFalse(state.hasStuckCheckPosition());
    }

    @Test
    void storesProgressCooldownAndCheckPosition() {
        AgentMovementStuckState state = new AgentMovementStuckState();

        state.addStuckMs(250);
        state.setUnstuckCooldownMs(5_000);
        state.setStuckCheckPosition(new Point(10, 20));

        assertEquals(250, state.stuckMs());
        assertEquals(5_000, state.unstuckCooldownMs());
        assertEquals(10, state.stuckCheckX());
        assertEquals(20, state.stuckCheckY());
        assertTrue(state.hasStuckCheckPosition());

        state.clearStuckCheckPosition();

        assertFalse(state.hasStuckCheckPosition());
        assertEquals(20, state.stuckCheckY());
    }
}
