package server.agents.capabilities.movement;

/**
 * Agent-owned movement tick timing helpers using the configured movement tick.
 */
public final class AgentMovementTimers {
    private AgentMovementTimers() {
    }

    public static int tickDown(int remainingMs) {
        return AgentMovementTimingPolicy.tickDown(
                remainingMs,
                AgentMovementPhysicsConfig.configuredMovementTickMs());
    }

    public static int delayAfterCurrentTick(int durationMs) {
        return AgentMovementTimingPolicy.delayAfterCurrentTick(
                durationMs,
                AgentMovementPhysicsConfig.configuredMovementTickMs());
    }
}
