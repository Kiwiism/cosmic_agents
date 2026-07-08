package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentFormationStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentFormationStateRuntimeTest {
    @Test
    void storesFollowOffset() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentFormationStateRuntime.setFollowOffsetX(entry, -60);

        assertEquals(-60, AgentFormationStateRuntime.followOffsetX(entry));
    }
}
