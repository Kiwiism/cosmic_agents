package server.agents.runtime;

import server.agents.capabilities.movement.AgentFormationOffsetState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentFormationOffsetStateTest {
    @Test
    void storesPerAgentFollowOffset() {
        AgentFormationOffsetState state = new AgentFormationOffsetState();

        assertEquals(0, state.followOffsetX());

        state.setFollowOffsetX(-60);

        assertEquals(-60, state.followOffsetX());
    }
}
