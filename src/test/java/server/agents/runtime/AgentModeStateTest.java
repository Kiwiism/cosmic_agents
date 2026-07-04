package server.agents.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentModeStateTest {
    @Test
    void tracksFollowAndGrindModes() {
        AgentModeState state = new AgentModeState();

        assertFalse(state.following());
        assertFalse(state.grinding());
        assertEquals(0, state.followTargetId());

        state.startFollowing(123);

        assertTrue(state.following());
        assertFalse(state.grinding());
        assertEquals(123, state.followTargetId());

        state.startGrinding();

        assertFalse(state.following());
        assertTrue(state.grinding());
        assertEquals(0, state.followTargetId());

        state.stopMovementModes();

        assertFalse(state.following());
        assertFalse(state.grinding());
        assertEquals(0, state.followTargetId());
    }
}
