package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementPhaseDispatchService;
import server.agents.capabilities.movement.AgentMovementPoseService;

import client.Character;
import server.bots.BotEntry;

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
                entry -> AgentMovementPhaseDispatchService.tickSwimming(asBotEntry(entry), null),
                entry -> AgentMovementPhaseDispatchService.tickAirborne(asBotEntry(entry), null),
                entry -> AgentMovementPoseService.resolveIdleGroundStance(asBotEntry(entry)),
                entry -> AgentMovementPoseService.resolveStance(asBotEntry(entry)),
                (entry, agent) -> AgentMovementPoseService.idleOnGround(asBotEntry(entry), agent),
                entry -> AgentMovementBroadcastService.broadcastMovement(asBotEntry(entry)));
    }

    private static BotEntry asBotEntry(AgentRuntimeEntry entry) {
        return (BotEntry) entry;
    }
}
