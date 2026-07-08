package server.agents.runtime;

import server.agents.integration.AgentMovementStateRuntime;

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
        NavigationResult navigation = hooks.navigationResolver().resolve(entry, targetPosition, runAiTick);
        if (navigation.consumedTick()) {
            return;
        }

        Point steeringTarget = navigation.targetPosition();
        AgentTickStateMaintenanceService.markPreciseNavigationTargetIfNeeded(entry);
        if (hooks.fidgetTick().tryHandle(entry, steeringTarget, runAiTick)) {
            return;
        }

        hooks.movementPhaseTick().tick(entry, steeringTarget, runAiTick);
        if (runAiTick && !AgentMovementStateRuntime.inAir(entry) && !AgentMovementStateRuntime.climbing(entry)) {
            hooks.committedEdgeExecutor().tryExecute(entry, targetPosition);
        }
        hooks.stuckDetection().run(entry);
        hooks.reachedMoveTargetCleanup().run(entry);
    }
}
