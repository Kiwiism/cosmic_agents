package server.agents.integration.cosmic;

import client.Character;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.capabilities.supplies.AgentPotionCheckRequestService;
import server.agents.capabilities.supplies.AgentPotionStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

public final class CosmicAgentPotionCheckRequestBridge {
    private CosmicAgentPotionCheckRequestBridge() {
    }

    public static void requestPotionCheckSoon(Character agent) {
        AgentPotionCheckRequestService.requestPotionCheckSoon(agent, hooks());
    }

    private static AgentPotionCheckRequestService.Hooks<AgentRuntimeEntry> hooks() {
        return new AgentPotionCheckRequestService.Hooks<>(
                CosmicAgentPotionCheckRequestBridge::resolveAgentEntry,
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
