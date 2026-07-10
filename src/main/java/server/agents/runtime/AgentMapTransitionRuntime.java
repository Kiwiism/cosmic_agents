package server.agents.runtime;

import server.agents.capabilities.movement.AgentMapTransitionService;
import server.agents.capabilities.movement.AgentMapGroundingCoordinator;

import client.Character;
import server.agents.capabilities.partyquest.AgentPartyQuestHooks;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.capabilities.partyquest.AgentPqRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;

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
        return AgentMapTransitionService.handleTrackedMapChange(
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
                        AgentManagerStatusRuntime::checkManagerStatus));
    }

}
