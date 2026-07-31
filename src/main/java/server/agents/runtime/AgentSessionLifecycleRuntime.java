package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentRelationshipRuntime;

import java.util.List;

/**
 * Agent runtime facade for live session registry and lifecycle actions.
 */
public final class AgentSessionLifecycleRuntime {
    private AgentSessionLifecycleRuntime() {
    }

    public static void reloginAgent(AgentReloginRequest request) {
        AgentInteractionRuntime.reloginAgent(request);
    }

    public static List<AgentRuntimeEntry> getCohortEntries(AgentRuntimeEntry entry) {
        return AgentRuntimeRegistry.entriesForCohort(AgentRelationshipRuntime.cohortId(entry));
    }

    public static Character activeLeaderByAgentCharacterId(int agentCharId) {
        return AgentRuntimeRegistry.activeLeaderByAgentCharacterId(agentCharId);
    }
}
