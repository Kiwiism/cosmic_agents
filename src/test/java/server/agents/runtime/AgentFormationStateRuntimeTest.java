package server.agents.runtime;

import server.agents.capabilities.movement.AgentFormationStateRuntime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentFormationStateRuntimeTest {
    @Test
    void storesFollowOffset() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentFormationStateRuntime.setFollowOffsetX(entry, -60);

        assertEquals(-60, AgentFormationStateRuntime.followOffsetX(entry));
    }
}
