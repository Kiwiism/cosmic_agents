package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementPhaseDispatchService;
import server.bots.BotEntry;

import java.awt.Point;

public final class AgentMovementPhaseRuntime {
    private AgentMovementPhaseRuntime() {
    }

    public static void tickMovementPhase(BotEntry entry, Point targetPosition, boolean runAiTick) {
        AgentMovementPhaseService.tickMovementPhase(entry, targetPosition, runAiTick, hooks());
    }

    private static AgentMovementPhaseService.MovementPhaseHooks hooks() {
        return new AgentMovementPhaseService.MovementPhaseHooks(
                (entry, target) -> AgentMapEnvironmentService.isSwimMap(entry),
                AgentMovementPhaseDispatchService::tickClimbing,
                AgentMovementPhaseDispatchService::tickSwimming,
                AgentMovementPhaseDispatchService::tickAirborne,
                AgentMovementPhaseDispatchService::tickGrounded);
    }
}
