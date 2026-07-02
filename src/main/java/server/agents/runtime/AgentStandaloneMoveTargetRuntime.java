package server.agents.runtime;

import client.Character;
import server.bots.BotEntry;
import server.bots.BotMovementManager;

public final class AgentStandaloneMoveTargetRuntime {
    private AgentStandaloneMoveTargetRuntime() {
    }

    public static void tickStandaloneMoveTarget(BotEntry entry,
                                                Character agent,
                                                boolean runAiTick) {
        tickStandaloneMoveTarget(
                entry,
                agent,
                runAiTick,
                AgentRuntimeConfig.cfg.ENABLE_UNSTUCK,
                BotMovementManager.configuredStopDist());
    }

    public static void tickStandaloneMoveTarget(BotEntry entry,
                                                Character agent,
                                                boolean runAiTick,
                                                boolean enableUnstuck,
                                                int stopDistance) {
        AgentStandaloneMoveTargetTickService.tickStandaloneMoveTarget(
                entry,
                agent,
                runAiTick,
                new AgentStandaloneMoveTargetTickService.Hooks(
                        AgentMapTransitionRuntime::groundAfterMapChange,
                        BotMovementManager::refreshMovementProfile,
                        (moveEntry, targetPosition, moveRunAiTick) -> AgentMovementTickRuntime.stepMovementCore(
                                moveEntry,
                                targetPosition,
                                moveRunAiTick,
                                enableUnstuck,
                                stopDistance)));
    }
}
