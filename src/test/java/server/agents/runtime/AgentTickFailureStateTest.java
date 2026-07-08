package server.agents.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentTickFailureStateTest {
    @Test
    void defaultsPreserveLegacyAgentRuntimeEntryValues() {
        AgentTickFailureState state = new AgentTickFailureState();

        assertEquals(0, state.failureCount());
        assertEquals(0L, state.windowStartedAtMs());
    }

    @Test
    void storesWindowAndFailureCount() {
        AgentTickFailureState state = new AgentTickFailureState();

        state.resetWindow(1_000L);
        assertEquals(1_000L, state.windowStartedAtMs());
        assertEquals(0, state.failureCount());

        assertEquals(1, state.incrementFailureCount());
        assertEquals(2, state.incrementFailureCount());

        state.clear();

        assertEquals(0, state.failureCount());
        assertEquals(0L, state.windowStartedAtMs());
    }
}
