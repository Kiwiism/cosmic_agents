package server.agents.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMovementBroadcastStateTest {
    @Test
    void defaultsPreserveLegacyBotEntryValues() {
        AgentMovementBroadcastState state = new AgentMovementBroadcastState();

        assertFalse(state.valid());
        assertEquals(0, state.x());
        assertEquals(0, state.y());
        assertEquals(0, state.velocityX());
        assertEquals(0, state.velocityY());
        assertEquals(0, state.stance());
        assertEquals(0, state.footholdId());
    }

    @Test
    void recordsMatchesAndInvalidatesMovementBroadcastSnapshot() {
        AgentMovementBroadcastState state = new AgentMovementBroadcastState();

        assertFalse(state.matches(10, 20, 1, 2, 3, 4));

        state.record(10, 20, 1, 2, 3, 4);

        assertTrue(state.matches(10, 20, 1, 2, 3, 4));
        assertFalse(state.matches(11, 20, 1, 2, 3, 4));
        assertFalse(state.matches(10, 20, 9, 2, 3, 4));
        assertFalse(state.matches(10, 20, 1, 2, 9, 4));
        assertFalse(state.matches(10, 20, 1, 2, 3, 9));

        state.setValid(false);

        assertFalse(state.matches(10, 20, 1, 2, 3, 4));
    }
}
