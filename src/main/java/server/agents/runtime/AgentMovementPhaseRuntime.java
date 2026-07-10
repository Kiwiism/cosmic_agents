package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementPhaseService;
import server.agents.capabilities.movement.AgentMovementPhaseDispatchService;
import server.agents.capabilities.movement.AgentMapEnvironmentService;

import java.awt.Point;

public final class AgentMovementPhaseRuntime {
    private AgentMovementPhaseRuntime() {
    }

    public static void tickMovementPhase(AgentRuntimeEntry entry, Point targetPosition, boolean runAiTick) {
        AgentMovementPhaseService.tickMovementPhase(entry, targetPosition, runAiTick, hooks(entry));
    }

    private static AgentMovementPhaseService.MovementPhaseHooks hooks(AgentRuntimeEntry entry) {
        return new AgentMovementPhaseService.MovementPhaseHooks(
                (candidate, target) -> AgentMapEnvironmentService.isSwimMap(candidate),
                (ignored, target, runAiTick) -> AgentMovementPhaseDispatchService.tickClimbing(entry, target, runAiTick),
                (ignored, target) -> AgentMovementPhaseDispatchService.tickSwimming(entry, target),
                (ignored, target) -> AgentMovementPhaseDispatchService.tickAirborne(entry, target),
                (ignored, target) -> AgentMovementPhaseDispatchService.tickGrounded(entry, target));
    }
}
