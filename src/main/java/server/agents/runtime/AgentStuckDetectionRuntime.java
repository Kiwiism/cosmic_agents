package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.bots.BotEntry;
import server.bots.BotMovementManager;

public final class AgentStuckDetectionRuntime {
    private AgentStuckDetectionRuntime() {
    }

    public static void tickStuckDetection(BotEntry entry, boolean enableUnstuck) {
        AgentStuckDetectionService.tickStuckDetection(
                entry,
                new AgentStuckDetectionService.StuckDetectionHooks(
                        BotMovementManager::tickDown,
                        BotMovementManager::tickUnstuck,
                        AgentMovementPhysicsConfig.configuredMovementTickMs(),
                        enableUnstuck));
    }
}
