package server.agents.capabilities.movement;

import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.scheduler.AgentSchedulerConfig;
import server.agents.runtime.simulation.AgentSimulationMode;

/**
 * Converts a throttled background movement tick into bounded physics-sized steps.
 */
public final class AgentMovementSubstepPolicy {
    private static final int MAX_SUBSTEPS = 5;
    private static final long BACKGROUND_ACTIVE_TICK_MS =
            AgentSchedulerConfig.fromSystemProperties().backgroundActiveTickMs();

    private AgentMovementSubstepPolicy() {
    }

    public static int substeps(AgentRuntimeEntry entry) {
        if (entry == null || entry.simulationState().mode() != AgentSimulationMode.BACKGROUND_ACTIVE) {
            return 1;
        }
        return substeps(BACKGROUND_ACTIVE_TICK_MS,
                AgentMovementPhysicsConfig.configuredMovementTickMs());
    }

    static int substeps(long scheduledTickMs, int movementTickMs) {
        if (scheduledTickMs <= 0L || movementTickMs <= 0) {
            return 1;
        }
        return Math.clamp((int) (scheduledTickMs / movementTickMs), 1, MAX_SUBSTEPS);
    }
}
