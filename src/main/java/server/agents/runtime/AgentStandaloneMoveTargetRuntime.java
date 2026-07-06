package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentMovementProfileService;

import client.Character;
import server.bots.BotEntry;

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
                AgentMovementPhysicsConfig.configuredStopDist());
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
                        (moveEntry, moveAgent) -> AgentMapTransitionRuntime.groundAfterMapChange(asBotEntry(moveEntry), moveAgent),
                        moveEntry -> AgentMovementProfileService.refreshMovementProfile(asBotEntry(moveEntry)),
                        (moveEntry, targetPosition, moveRunAiTick) -> AgentMovementTickRuntime.stepMovementCore(
                                asBotEntry(moveEntry),
                                targetPosition,
                                moveRunAiTick,
                                enableUnstuck,
                                stopDistance)));
    }

    private static BotEntry asBotEntry(AgentRuntimeEntry entry) {
        return (BotEntry) entry;
    }
}
