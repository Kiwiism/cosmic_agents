package server.agents.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentGrindWanderStateTest {
    @Test
    void normalizesDirectionToSign() {
        AgentGrindWanderState state = new AgentGrindWanderState();

        assertEquals(0, state.direction());

        state.setDirection(10);
        assertEquals(1, state.direction());

        state.setDirection(-10);
        assertEquals(-1, state.direction());

        state.setDirection(0);
        assertEquals(0, state.direction());
    }

    @Test
    void clearResetsDirection() {
        AgentGrindWanderState state = new AgentGrindWanderState();

        state.setDirection(1);
        state.clear();

        assertEquals(0, state.direction());
    }
}
