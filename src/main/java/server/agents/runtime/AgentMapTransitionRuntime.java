package server.agents.runtime;

import server.agents.capabilities.movement.AgentMapTransitionService;
import server.agents.capabilities.movement.AgentMapGroundingCoordinator;

import client.Character;
import server.agents.capabilities.partyquest.AgentPartyQuestHooks;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.capabilities.partyquest.AgentPqRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.capabilities.dialogue.AgentChatStatusOrchestrator;
import server.agents.diagnostics.AgentRunObservationRuntime;

import java.util.function.Consumer;

public final class AgentMapTransitionRuntime {
    private AgentMapTransitionRuntime() {
    }

    public static boolean groundAfterMapChange(AgentRuntimeEntry entry, Character agent) {
        return AgentMapGroundingCoordinator.groundAfterMapChange(entry, agent);
    }

    public static boolean handleTrackedMapChange(AgentRuntimeEntry entry,
                                                 Character agent,
                                                 Consumer<AgentRuntimeEntry> issueGrind,
                                                 Consumer<AgentRuntimeEntry> issueFollow) {
        boolean changed = AgentMapTransitionService.handleTrackedMapChange(
                entry,
                agent,
                new AgentMapTransitionService.MapChangeHooks(
                        AgentMapGroundingCoordinator.groundingHooks(),
                        AgentPartyQuestHooks::requiresGrind,
                        issueGrind,
                        AgentPartyQuestHooks::requiresFollow,
                        issueFollow,
                        AgentPqRuntime::resetKpqStage5Claimed,
                        (shopEntry, shopAgent) -> AgentShopService.onMapChange(
                                shopEntry, shopAgent, AgentInventoryGatewayRuntime.inventory()),
                        AgentChatStatusOrchestrator::checkBotStatus));
        if (changed) {
            AgentRunObservationRuntime.mapChanged(entry, agent, System.currentTimeMillis());
        }
        return changed;
    }

}
