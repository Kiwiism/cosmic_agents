package server.agents.capabilities.movement;

import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.navigation.AgentNavigationPreciseTargetService;

import java.awt.Point;

/**
 * Agent-owned movement-core tick orchestration.
 */
public final class AgentMovementTickService {
    @FunctionalInterface
    public interface NavigationResolver {
        NavigationResult resolve(AgentRuntimeEntry entry, Point targetPosition, boolean runAiTick);
    }

    @FunctionalInterface
    public interface FidgetTick {
        boolean tryHandle(AgentRuntimeEntry entry, Point targetPosition, boolean runAiTick);
    }

    @FunctionalInterface
    public interface FidgetMovementSubstep {
        boolean tick(AgentRuntimeEntry entry, Point targetPosition);
    }

    @FunctionalInterface
    public interface MovementPhaseTick {
        void tick(AgentRuntimeEntry entry, Point targetPosition, boolean runAiTick);
    }

    @FunctionalInterface
    public interface CommittedEdgeExecutor {
        void tryExecute(AgentRuntimeEntry entry, Point targetPosition);
    }

    public record NavigationResult(boolean consumedTick, Point targetPosition) {
    }

    public record MovementTickHooks(NavigationResolver navigationResolver,
                                    FidgetTick fidgetTick,
                                    FidgetMovementSubstep fidgetMovementSubstep,
                                    MovementPhaseTick movementPhaseTick,
                                    CommittedEdgeExecutor committedEdgeExecutor,
                                    RunnableTick stuckDetection,
                                    RunnableTick reachedMoveTargetCleanup) {
    }

    @FunctionalInterface
    public interface RunnableTick {
        void run(AgentRuntimeEntry entry);
    }

    private AgentMovementTickService() {
    }

    public static void stepMovementCore(AgentRuntimeEntry entry,
                                        Point targetPosition,
                                        boolean runAiTick,
                                        MovementTickHooks hooks) {
        stepMovementCore(entry, targetPosition, runAiTick, 1, hooks);
    }

    public static void stepMovementCore(AgentRuntimeEntry entry,
                                        Point targetPosition,
                                        boolean runAiTick,
                                        int movementSubsteps,
                                        MovementTickHooks hooks) {
        NavigationResult navigation = hooks.navigationResolver().resolve(entry, targetPosition, runAiTick);
        if (navigation.consumedTick()) {
            return;
        }

        Point steeringTarget = navigation.targetPosition();
        AgentNavigationPreciseTargetService.markPreciseNavigationTargetIfNeeded(entry);
        if (hooks.fidgetTick().tryHandle(entry, steeringTarget, runAiTick)) {
            for (int step = 1; step < Math.max(1, movementSubsteps); step++) {
                if (!hooks.fidgetMovementSubstep().tick(entry, steeringTarget)) {
                    break;
                }
            }
            return;
        }

        for (int step = 0; step < Math.max(1, movementSubsteps); step++) {
            hooks.movementPhaseTick().tick(entry, steeringTarget, step == 0 && runAiTick);
        }
        if (runAiTick && !AgentMovementStateRuntime.inAir(entry) && !AgentMovementStateRuntime.climbing(entry)) {
            hooks.committedEdgeExecutor().tryExecute(entry, targetPosition);
        }
        hooks.stuckDetection().run(entry);
        hooks.reachedMoveTargetCleanup().run(entry);
    }
}
