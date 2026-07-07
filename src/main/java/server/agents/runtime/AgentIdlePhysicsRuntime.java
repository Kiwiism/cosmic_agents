package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementPhaseDispatchService;
import server.agents.capabilities.movement.AgentMovementPoseService;

import client.Character;

public final class AgentIdlePhysicsRuntime {
    private AgentIdlePhysicsRuntime() {
    }

    public static void tickPhysicsOnly(AgentRuntimeEntry entry, Character agent) {
        AgentIdlePhysicsService.tickPhysicsOnly(entry, agent, hooks());
    }

    public static boolean tickIdleEntry(AgentRuntimeEntry entry, Character agent) {
        return AgentIdlePhysicsService.tickIdleEntry(entry, agent, hooks());
    }

    private static AgentIdlePhysicsService.PhysicsHooks hooks() {
        return new AgentIdlePhysicsService.PhysicsHooks(
                AgentMapEnvironmentService::isSwimMap,
                entry -> AgentMovementPhaseDispatchService.tickSwimming(entry, null),
                entry -> AgentMovementPhaseDispatchService.tickAirborne(entry, null),
                AgentMovementPoseService::resolveIdleGroundStance,
                AgentMovementPoseService::resolveStance,
                AgentMovementPoseService::idleOnGround,
                AgentMovementBroadcastService::broadcastMovement);
    }
}
