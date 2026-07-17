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

    public static void reloginBot(int charId, int ownerCharId, int world, int channel) {
        AgentInteractionRuntime.reloginAgent(charId, ownerCharId, world, channel);
    }

    public static void reloginAgent(AgentReloginRequest request) {
        AgentInteractionRuntime.reloginAgent(request);
    }

    public static List<AgentRuntimeEntry> getBotEntries(int ownerCharId) {
        return AgentRuntimeRegistry.agentEntriesForLeader(ownerCharId);
    }

    public static List<AgentRuntimeEntry> getCohortEntries(AgentRuntimeEntry entry) {
        return AgentRuntimeRegistry.entriesForCohort(AgentRelationshipRuntime.cohortId(entry));
    }

    public static AgentRuntimeEntry getAgentEntry(int ownerCharId, String agentName) {
        return AgentRuntimeRegistry.findByName(ownerCharId, agentName);
    }

    public static void issueOwnerAwaySafeModeForLeader(int ownerCharId, boolean town) {
        AgentLeaderSafetyCoordinator.issueInactiveSafeModeForLeader(ownerCharId, town);
    }

    public static Character activeLeaderByAgentCharacterId(int agentCharId) {
        return AgentRuntimeRegistry.activeLeaderByAgentCharacterId(agentCharId);
    }
}
