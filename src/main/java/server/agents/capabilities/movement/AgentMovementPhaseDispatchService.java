package server.agents.capabilities.movement;

import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

public final class AgentMovementPhaseDispatchService {
    private AgentMovementPhaseDispatchService() {
    }

    public static void tickClimbing(AgentRuntimeEntry entry, Point targetPos, boolean runAiTick) {
        AgentClimbMovementService.tickClimbing(entry, targetPos, runAiTick);
    }

    public static void tickSwimming(AgentRuntimeEntry entry, Point targetPos) {
        AgentSwimMovementService.tickSwimming(entry, targetPos);
    }

    public static void tickAirborne(AgentRuntimeEntry entry, Point targetPos) {
        AgentAirborneMovementService.tickAirborne(entry, targetPos);
    }

    public static void tickGrounded(AgentRuntimeEntry entry, Point targetPos) {
        AgentGroundMovementRuntimeService.tickGrounded(entry, targetPos);
    }

}
