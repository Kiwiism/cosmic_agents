package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotManagerSchedulerRuntime;

public final class AgentRuntimeCleanupService {
    private AgentRuntimeCleanupService() {
    }

    public static boolean removeAgentByCharacterId(int agentCharId) {
        return AgentLifecycleService.removeAgentByCharacterId(
                AgentRuntimeRegistry.entriesByLeaderId(),
                AgentFormationService.formationsByLeaderId(),
                AgentLeaderSafetyService.townClusterAnchorsByLeaderId(),
                agentCharId,
                AgentBotManagerSchedulerRuntime::cancelScheduledTask);
    }

    public static boolean cleanupAgentRuntimeState(Character agent) {
        if (agent == null) {
            return false;
        }

        boolean removed = removeAgentByCharacterId(agent.getId());
        AgentAutopotRuntimeCleanupService.clearBotOnlyAutopotState(agent);
        return removed;
    }
}
