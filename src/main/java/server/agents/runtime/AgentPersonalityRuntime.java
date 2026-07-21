package server.agents.runtime;

import client.Character;
import server.agents.model.AgentId;
import server.agents.model.AgentIdentity;
import server.agents.personality.AgentPersonalityAssignmentService;
import server.agents.personality.AgentPersonalityProfile;
import server.agents.personality.AgentPersonalityState;

/** Runtime adapter binding durable personality identity to one live Agent session. */
public final class AgentPersonalityRuntime {
    private AgentPersonalityRuntime() {
    }

    public static AgentPersonalityProfile restoreOrAssign(
            AgentRuntimeEntry entry, boolean presentationEnabled, long nowMs) {
        Character agent = entry == null ? null : entry.bot();
        if (agent == null || agent.getId() <= 0
                || agent.getName() == null || agent.getName().isBlank()) {
            return null;
        }
        AgentPersonalityState state = entry.capabilityStates().require(
                AgentPersonalityState.STATE_KEY);
        return AgentPersonalityAssignmentService.restoreOrAssign(
                state,
                new AgentIdentity(new AgentId(agent.getId()), agent.getName()),
                presentationEnabled,
                nowMs);
    }
}
