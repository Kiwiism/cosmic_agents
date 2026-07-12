package server.agents.capabilities.runtime;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

public record AgentCapabilityContext(
        AgentRuntimeEntry entry,
        Character agent,
        long nowMs,
        long elapsedMs,
        int retryCount,
        AgentCapabilityResult childResult,
        AgentCapabilityMemory memory) {

    public AgentCapabilityContext(AgentRuntimeEntry entry,
                                  Character agent,
                                  long nowMs,
                                  long elapsedMs,
                                  int retryCount,
                                  AgentCapabilityResult childResult) {
        this(entry, agent, nowMs, elapsedMs, retryCount, childResult, new AgentCapabilityMemory());
    }
}
