package server.agents.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTickStateTest {
    @Test
    void defaultsPreserveLegacyBotEntryValues() {
        AgentTickState state = new AgentTickState();

        assertFalse(state.lastTickWasAi());
        assertEquals(0L, state.lastTickAtMs());
        assertEquals(0L, state.lastHeartbeatAtMs());
        assertEquals(0L, state.nextFollowIdleMovementCheckAtMs());
        assertTrue(state.heartbeatDue(600_000L, 600_000L));
    }

    @Test
    void storesTickHeartbeatAndFollowIdleTimers() {
        AgentTickState state = new AgentTickState();

        state.recordTick(true, 1_234L);
        state.setLastHeartbeatAtMs(600_000L);
        state.setNextFollowIdleMovementCheckAtMs(2_000L);

        assertTrue(state.lastTickWasAi());
        assertEquals(1_234L, state.lastTickAtMs());
        assertEquals(600_000L, state.lastHeartbeatAtMs());
        assertFalse(state.heartbeatDue(600_001L, 600_000L));
        assertTrue(state.heartbeatDue(1_200_000L, 600_000L));
        assertEquals(2_000L, state.nextFollowIdleMovementCheckAtMs());
    }
}
