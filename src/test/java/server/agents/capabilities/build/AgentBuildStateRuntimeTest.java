package server.agents.capabilities.build;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentBuildStateRuntimeTest {
    @Test
    void adaptsLevelAndJobPromptState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(-1, AgentBuildStateRuntime.lastKnownLevel(entry));
        assertEquals(0, AgentBuildStateRuntime.jobPromptSent(entry));

        AgentBuildStateRuntime.setLastKnownLevel(entry, 30);
        AgentBuildStateRuntime.setJobPromptSent(entry, 30);

        assertEquals(30, AgentBuildStateRuntime.lastKnownLevel(entry));
        assertEquals(30, AgentBuildStateRuntime.jobPromptSent(entry));
    }
}
