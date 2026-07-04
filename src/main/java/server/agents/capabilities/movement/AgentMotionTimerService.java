package server.agents.capabilities.movement;

import server.bots.BotEntry;
import server.agents.integration.AgentBotMovementStateRuntime;

/**
 * Agent-owned seam for movement physics timer countdowns.
 */
public final class AgentMotionTimerService {
    private AgentMotionTimerService() {
    }

    public static void tickMotionTimers(BotEntry entry) {
        if (AgentBotMovementStateRuntime.downJumpGracePeriodMs(entry) > 0L) {
            AgentBotMovementStateRuntime.setDownJumpGracePeriodMs(entry,
                    Math.max(0L, AgentBotMovementStateRuntime.downJumpGracePeriodMs(entry)
                            - AgentMovementPhysicsConfig.configuredMovementTickMs()));
        }
    }
}
