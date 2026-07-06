package server.agents.capabilities.movement;

import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned seam for movement physics timer countdowns.
 */
public final class AgentMotionTimerService {
    private AgentMotionTimerService() {
    }

    public static void tickMotionTimers(AgentRuntimeEntry entry) {
        if (AgentBotMovementStateRuntime.downJumpGracePeriodMs(entry) > 0L) {
            AgentBotMovementStateRuntime.setDownJumpGracePeriodMs(entry,
                    Math.max(0L, AgentBotMovementStateRuntime.downJumpGracePeriodMs(entry)
                            - AgentMovementPhysicsConfig.configuredMovementTickMs()));
        }
    }
}
