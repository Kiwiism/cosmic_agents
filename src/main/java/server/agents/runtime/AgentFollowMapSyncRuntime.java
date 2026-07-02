package server.agents.runtime;

import client.Character;
import server.bots.BotEntry;
import server.bots.BotMovementManager;
import server.bots.BotPhysicsEngine;

public final class AgentFollowMapSyncRuntime {
    private AgentFollowMapSyncRuntime() {
    }

    public static boolean syncFollowMap(BotEntry entry, Character agent, Character followAnchor) {
        return AgentFollowMapSyncService.syncFollowMap(
                entry,
                agent,
                followAnchor,
                new AgentFollowMapSyncService.FollowMapSyncHooks(
                        BotPhysicsEngine::findGroundPoint,
                        BotPhysicsEngine::idleOnGround,
                        Character::changeMap,
                        BotMovementManager::resetEntryState));
    }
}
