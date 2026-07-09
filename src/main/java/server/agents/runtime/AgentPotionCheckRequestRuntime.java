package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.capabilities.supplies.AgentPotionCheckRequestService;
import server.agents.capabilities.supplies.AgentPotionStateRuntime;

public final class AgentPotionCheckRequestRuntime {
    private AgentPotionCheckRequestRuntime() {
    }

    public static void requestPotionCheckSoon(Character agent) {
        AgentPotionCheckRequestService.requestPotionCheckSoon(agent, hooks());
    }

    private static AgentPotionCheckRequestService.Hooks<AgentRuntimeEntry> hooks() {
        return new AgentPotionCheckRequestService.Hooks<>(
                AgentPotionCheckRequestRuntime::resolveAgentEntry,
                AgentPotionStateRuntime::requestPotionCheckSoon);
    }

    private static AgentRuntimeEntry resolveAgentEntry(Character agent) {
        if (!AgentCharacterGatewayRuntime.characters().isAgentCharacter(agent)) {
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
