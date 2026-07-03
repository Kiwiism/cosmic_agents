package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementStateResetService;

import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentFootholdIndexService;

import client.Character;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.integration.AgentBotManagerStatusRuntime;
import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;

public final class AgentMovementOnlyMapChangeRuntime {
    private AgentMovementOnlyMapChangeRuntime() {
    }

    public static boolean handleMapChange(BotEntry entry, Character agent) {
        return AgentMovementOnlyMapChangeService.handleMapChange(
                entry,
                agent,
                new AgentMovementOnlyMapChangeService.Hooks(
                        AgentFootholdIndexService::buildFhIndex,
                        BotPhysicsEngine::findGroundPoint,
                        BotPhysicsEngine::teleportTo,
                        AgentMovementStateResetService::resetEntryStateAfterTeleport,
                        AgentMovementBroadcastService::broadcastMovement,
                        AgentShopService::onMapChange,
                        AgentBotManagerStatusRuntime::checkManagerStatus));
    }
}
