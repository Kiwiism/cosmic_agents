package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementBroadcastService;

import client.Character;
import server.bots.BotEntry;
import server.bots.BotMovementManager;
import server.bots.BotPhysicsEngine;

public final class AgentIdlePhysicsRuntime {
    private AgentIdlePhysicsRuntime() {
    }

    public static void tickPhysicsOnly(BotEntry entry, Character agent) {
        AgentIdlePhysicsService.tickPhysicsOnly(entry, agent, hooks());
    }

    public static boolean tickIdleEntry(BotEntry entry, Character agent) {
        return AgentIdlePhysicsService.tickIdleEntry(entry, agent, hooks());
    }

    private static AgentIdlePhysicsService.PhysicsHooks hooks() {
        return new AgentIdlePhysicsService.PhysicsHooks(
                AgentMapEnvironmentService::isSwimMap,
                entry -> BotMovementManager.tickSwimming(entry, null),
                entry -> BotMovementManager.tickAirborne(entry, null),
                BotPhysicsEngine::resolveIdleGroundStance,
                BotPhysicsEngine::resolveStance,
                BotPhysicsEngine::idleOnGround,
                AgentMovementBroadcastService::broadcastMovement);
    }
}
