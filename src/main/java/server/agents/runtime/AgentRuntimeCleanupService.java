package server.agents.runtime;

import server.agents.capabilities.supplies.AgentAutopotCleanupService;

import client.Character;

public final class AgentRuntimeCleanupService {
    private AgentRuntimeCleanupService() {
    }

    public static void removeAgentsForLeader(int leaderCharId) {
        AgentLifecycleService.removeLeaderEntries(
                AgentRuntimeRegistry.entriesByLeaderId(),
                AgentFormationService.formationsByLeaderId(),
                AgentLeaderSafetyService.townClusterAnchorsByLeaderId(),
                leaderCharId,
                AgentLifecycleService::cancelScheduledTickIfPresent);
    }

    public static boolean removeAgentByCharacterId(int agentCharId) {
        return AgentLifecycleService.removeAgentByCharacterId(
                AgentRuntimeRegistry.entriesByLeaderId(),
                AgentFormationService.formationsByLeaderId(),
                AgentLeaderSafetyService.townClusterAnchorsByLeaderId(),
                agentCharId,
                AgentLifecycleService::cancelScheduledTickIfPresent);
    }

    public static boolean cleanupAgentRuntimeState(Character agent) {
        if (agent == null) {
            return false;
        }

        boolean removed = removeAgentByCharacterId(agent.getId());
        AgentAutopotCleanupService.clearAgentAutopotState(agent);
        return removed;
    }
}
