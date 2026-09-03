package server.agents.capabilities.expedition;

import client.Character;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentModeService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.List;

/** Leaves returned expedition fixtures visibly wandering in their current lobby region. */
final class AgentExpeditionLobbyIdleService {
    private AgentExpeditionLobbyIdleService() {
    }

    static void begin(List<Character> members) {
        for (Character member : members) {
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.getId());
            if (entry == null || AgentRuntimeIdentityRuntime.bot(entry) != member
                    || member.getMap() == null) {
                continue;
            }
            AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(
                    member.getMap(), AgentMovementStateRuntime.movementProfile(entry));
            int regionId = graph == null
                    ? -1 : graph.findRegionId(member.getMap(), member.getPosition());
            if (regionId >= 0) {
                AgentModeService.startPatrol(
                        entry, regionId, AgentMovementStateResetService::clearNavigationState);
            } else {
                AgentModeService.startStop(entry);
            }
        }
    }
}
