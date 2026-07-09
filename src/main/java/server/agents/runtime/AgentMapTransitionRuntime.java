package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementStateResetService;

import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentFootholdIndexService;
import server.agents.capabilities.movement.AgentGroundingService;
import server.agents.capabilities.movement.AgentMovementPoseService;

import client.Character;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.partyquest.AgentPartyQuestHooks;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.capabilities.partyquest.AgentPqRuntime;
import server.agents.integration.cosmic.CosmicAgentServerAdapter;

import java.util.function.Consumer;

public final class AgentMapTransitionRuntime {
    private AgentMapTransitionRuntime() {
    }

    public static boolean groundAfterMapChange(AgentRuntimeEntry entry, Character agent) {
        return AgentMapTransitionService.groundAfterMapChange(entry, agent, groundingHooks());
    }

    public static boolean handleTrackedMapChange(AgentRuntimeEntry entry,
                                                 Character agent,
                                                 Consumer<AgentRuntimeEntry> issueGrind,
                                                 Consumer<AgentRuntimeEntry> issueFollow) {
        return AgentMapTransitionService.handleTrackedMapChange(
                entry,
                agent,
                new AgentMapTransitionService.MapChangeHooks(
                        groundingHooks(),
                        AgentPartyQuestHooks::requiresGrind,
                        issueGrind,
                        AgentPartyQuestHooks::requiresFollow,
                        issueFollow,
                        AgentPqRuntime::resetKpqStage5Claimed,
                        (shopEntry, shopAgent) -> AgentShopService.onMapChange(
                                shopEntry, shopAgent, CosmicAgentServerAdapter.INSTANCE.inventory()),
                        AgentManagerStatusRuntime::checkManagerStatus));
    }

    private static AgentMapTransitionService.GroundingHooks groundingHooks() {
        return new AgentMapTransitionService.GroundingHooks(
                AgentFootholdIndexService::buildFhIndex,
                AgentGroundingService::findGroundPoint,
                AgentMovementPoseService::teleportTo,
                AgentMovementStateResetService::resetEntryStateAfterTeleport,
                AgentNavigationGraphService::warmGraphAsync,
                AgentMovementBroadcastService::broadcastMovement);
    }
}
