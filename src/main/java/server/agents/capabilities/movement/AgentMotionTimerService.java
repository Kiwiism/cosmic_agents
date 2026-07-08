package server.agents.capabilities.movement;

import server.agents.integration.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned seam for movement physics timer countdowns.
 */
public final class AgentMotionTimerService {
    private AgentMotionTimerService() {
    }

    public static void tickMotionTimers(AgentRuntimeEntry entry) {
        if (AgentMovementStateRuntime.downJumpGracePeriodMs(entry) > 0L) {
            AgentMovementStateRuntime.setDownJumpGracePeriodMs(entry,
                    Math.max(0L, AgentMovementStateRuntime.downJumpGracePeriodMs(entry)
                            - AgentMovementPhysicsConfig.configuredMovementTickMs()));
        }
    }
}
