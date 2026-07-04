package server.agents.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBreakoutStateTest {
    @Test
    void storesCommitmentDirectionAndExpiry() {
        AgentBreakoutState state = new AgentBreakoutState();

        assertFalse(state.hasCommitment());

        state.setCommitment(-1, 2_000L);

        assertTrue(state.hasCommitment());
        assertEquals(-1, state.direction());
        assertEquals(2_000L, state.untilMs());
        assertFalse(state.expired(1_999L));
        assertTrue(state.expired(2_000L));
    }

    @Test
    void preservesLegacyDirectionWithoutNormalization() {
        AgentBreakoutState state = new AgentBreakoutState();

        state.setCommitment(7, 2_000L);

        assertEquals(7, state.direction());
    }

    @Test
    void clearResetsCommitment() {
        AgentBreakoutState state = new AgentBreakoutState();

        state.setCommitment(1, 2_000L);
        state.clear();

        assertFalse(state.hasCommitment());
        assertEquals(0, state.direction());
        assertEquals(0L, state.untilMs());
    }
}
