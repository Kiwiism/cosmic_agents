package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementStateResetService;

import server.agents.capabilities.movement.AgentMovementBroadcastService;

import client.Character;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.partyquest.AgentPartyQuestHooks;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.integration.AgentBotManagerStatusRuntime;
import server.agents.integration.AgentBotPqRuntime;
import server.bots.BotEntry;
import server.bots.BotMovementManager;
import server.bots.BotPhysicsEngine;

import java.util.function.Consumer;

public final class AgentMapTransitionRuntime {
    private AgentMapTransitionRuntime() {
    }

    public static boolean groundAfterMapChange(BotEntry entry, Character agent) {
        return AgentMapTransitionService.groundAfterMapChange(entry, agent, groundingHooks());
    }

    public static boolean handleTrackedMapChange(BotEntry entry,
                                                 Character agent,
                                                 Consumer<BotEntry> issueGrind,
                                                 Consumer<BotEntry> issueFollow) {
        return AgentMapTransitionService.handleTrackedMapChange(
                entry,
                agent,
                new AgentMapTransitionService.MapChangeHooks(
                        groundingHooks(),
                        AgentPartyQuestHooks::requiresGrind,
                        issueGrind,
                        AgentPartyQuestHooks::requiresFollow,
                        issueFollow,
                        AgentBotPqRuntime::resetKpqStage5Claimed,
                        AgentShopService::onMapChange,
                        AgentBotManagerStatusRuntime::checkManagerStatus));
    }

    private static AgentMapTransitionService.GroundingHooks groundingHooks() {
        return new AgentMapTransitionService.GroundingHooks(
                BotMovementManager::buildFhIndex,
                BotPhysicsEngine::findGroundPoint,
                BotPhysicsEngine::teleportTo,
                AgentMovementStateResetService::resetEntryStateAfterTeleport,
                AgentNavigationGraphService::warmGraphAsync,
                AgentMovementBroadcastService::broadcastMovement);
    }
}
