package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDegenerateAttackStateTest {
    @Test
    void marksAndClearsLatch() {
        AgentDegenerateAttackState state = new AgentDegenerateAttackState();

        assertFalse(state.done());

        state.markDone();

        assertTrue(state.done());

        state.clear();

        assertFalse(state.done());
    }
}
