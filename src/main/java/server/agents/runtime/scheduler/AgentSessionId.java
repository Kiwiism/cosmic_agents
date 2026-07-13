package server.agents.runtime.scheduler;

import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

public record AgentSessionId(int agentCharacterId, long generation) {
    public static AgentSessionId from(AgentRuntimeEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Agent runtime entry is required");
        }
        return new AgentSessionId(
                AgentRuntimeIdentityRuntime.botId(entry),
                entry.sessionGeneration());
    }

    public boolean matches(AgentRuntimeEntry entry) {
        return entry != null
                && agentCharacterId == AgentRuntimeIdentityRuntime.botId(entry)
                && generation == entry.sessionGeneration();
    }
}
