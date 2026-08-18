package server.agents.field;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFieldPlatformLeaseStateTest {
    @Test
    void requiresSevenSecondsOfContinuousEmptyPopulation() {
        AgentFieldPlatformLeaseState state = new AgentFieldPlatformLeaseState();

        assertFalse(state.releasable(0, 1_000L, 7_000L));
        assertFalse(state.releasable(0, 7_999L, 7_000L));
        assertTrue(state.releasable(0, 8_000L, 7_000L));
        assertEquals(7_000L, state.emptyForMs(8_000L));
    }

    @Test
    void aRespawnResetsTheVacancyWindow() {
        AgentFieldPlatformLeaseState state = new AgentFieldPlatformLeaseState();
        state.releasable(0, 1_000L, 7_000L);

        assertFalse(state.releasable(2, 6_000L, 7_000L));
        assertEquals(0L, state.emptyForMs(6_000L));
        assertFalse(state.releasable(0, 6_001L, 7_000L));
        assertFalse(state.releasable(0, 13_000L, 7_000L));
        assertTrue(state.releasable(0, 13_001L, 7_000L));
    }
}
