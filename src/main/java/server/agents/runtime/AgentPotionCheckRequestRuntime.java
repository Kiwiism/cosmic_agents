package server.agents.runtime;

import client.BotClient;
import client.Character;
import server.agents.capabilities.supplies.AgentPotionCheckRequestService;
import server.agents.integration.AgentBotPotionStateRuntime;

public final class AgentPotionCheckRequestRuntime {
    private AgentPotionCheckRequestRuntime() {
    }

    public static void requestPotionCheckSoon(Character agent) {
        AgentPotionCheckRequestService.requestPotionCheckSoon(agent, hooks());
    }

    private static AgentPotionCheckRequestService.Hooks<AgentRuntimeEntry> hooks() {
        return new AgentPotionCheckRequestService.Hooks<>(
                AgentPotionCheckRequestRuntime::resolveAgentEntry,
                AgentBotPotionStateRuntime::requestPotionCheckSoon);
    }

    private static AgentRuntimeEntry resolveAgentEntry(Character agent) {
        if (agent == null || !(agent.getClient() instanceof BotClient)) {
            return null;
        }
        Character leader = AgentRuntimeRegistry.activeLeaderByAgentCharacterId(
                AgentRuntimeRegistry.entriesByLeaderId(), agent.getId());
        if (leader == null) {
            return null;
        }
        return AgentRuntimeRegistry.findByCharacterId(
                AgentRuntimeRegistry.entriesByLeaderId(), leader.getId(), agent.getId());
    }
}
