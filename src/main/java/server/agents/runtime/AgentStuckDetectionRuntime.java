package server.agents.runtime;

import server.bots.BotEntry;
import server.bots.BotMovementManager;
import server.bots.BotPhysicsEngine;

public final class AgentStuckDetectionRuntime {
    private AgentStuckDetectionRuntime() {
    }

    public static void tickStuckDetection(BotEntry entry, boolean enableUnstuck) {
        AgentStuckDetectionService.tickStuckDetection(
                entry,
                new AgentStuckDetectionService.StuckDetectionHooks(
                        BotMovementManager::tickDown,
                        BotMovementManager::tickUnstuck,
                        BotPhysicsEngine.movementTickMs(),
                        enableUnstuck));
    }
}
