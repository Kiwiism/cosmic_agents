package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotBuildStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentBotBuildStateRuntimeTest {
    @Test
    void adaptsLevelAndJobPromptState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(-1, AgentBotBuildStateRuntime.lastKnownLevel(entry));
        assertEquals(0, AgentBotBuildStateRuntime.jobPromptSent(entry));

        AgentBotBuildStateRuntime.setLastKnownLevel(entry, 30);
        AgentBotBuildStateRuntime.setJobPromptSent(entry, 30);

        assertEquals(30, AgentBotBuildStateRuntime.lastKnownLevel(entry));
        assertEquals(30, AgentBotBuildStateRuntime.jobPromptSent(entry));
    }
}
