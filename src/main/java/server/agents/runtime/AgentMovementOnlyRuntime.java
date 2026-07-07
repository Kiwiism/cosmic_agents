package server.agents.runtime;

import client.Character;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.integration.AgentBotShopStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;
import java.util.function.BiFunction;

public final class AgentMovementOnlyRuntime {
    private AgentMovementOnlyRuntime() {
    }

    public static void stepMovementOnly(AgentRuntimeEntry entry,
                                        Point targetPosition,
                                        boolean runAiTick,
                                        long nowMs,
                                        BiFunction<BotEntry, Character, Character> followAnchorResolver,
                                        MovementOnlyConfig config) {
        AgentMovementOnlyTickService.stepMovementOnly(
                entry,
                targetPosition,
                runAiTick,
                nowMs,
                hooks(followAnchorResolver, config));
    }

    private static AgentMovementOnlyTickService.MovementOnlyHooks hooks(
            BiFunction<BotEntry, Character, Character> followAnchorResolver,
            MovementOnlyConfig config) {
        return new AgentMovementOnlyTickService.MovementOnlyHooks(
                (entry, agent) -> AgentIdlePhysicsRuntime.tickIdleEntry(asBotEntry(entry), agent),
                (entry, agent) -> AgentBotShopStateRuntime.shopVisitPending(entry),
                (entry, agent, leader) -> AgentFollowMapSyncRuntime.syncFollowMap(asBotEntry(entry), agent, leader),
                (entry, leader) -> followAnchorResolver.apply(asBotEntry(entry), leader),
                (entry, agent, anchor) -> AgentRecoveryTeleportRuntime.recoverGrindPartyTeleportDistance(
                        asBotEntry(entry),
                        agent,
                        anchor,
                        config.teleportDistance(),
                        config.outOfBoundsTeleportDistance(),
                        config.grindPartyTeleportDistanceMultiplier()),
                (entry, agent, target) -> AgentRecoveryTeleportRuntime.recoverTeleportDistance(
                        asBotEntry(entry),
                        agent,
                        target,
                        config.teleportDistance(),
                        config.outOfBoundsTeleportDistance()),
                (entry, agent) -> AgentMovementOnlyMapChangeRuntime.handleMapChange(asBotEntry(entry), agent),
                (entry, agent) -> AgentShopService.tickShopVisit(asBotEntry(entry), agent),
                AgentBotShopStateRuntime::activeShopTargetPosition,
                AgentBotShopStateRuntime::shopApproachDelayMs,
                (entry, agent, target, nowMs) -> AgentFollowIdleMovementService.tryFollowIdleMovementFastPath(
                        entry,
                        agent,
                        target,
                        nowMs,
                        config.followDistance(),
                        config.stopDistance()),
                (entry, target, coreRunAiTick) -> AgentMovementTickRuntime.stepMovementCore(
                        asBotEntry(entry),
                        target,
                        coreRunAiTick,
                        config.enableUnstuck(),
                        config.stopDistance()));
    }

    private static BotEntry asBotEntry(AgentRuntimeEntry entry) {
        return (BotEntry) entry;
    }

    public record MovementOnlyConfig(int teleportDistance,
                                     int outOfBoundsTeleportDistance,
                                     int grindPartyTeleportDistanceMultiplier,
                                     int followDistance,
                                     int stopDistance,
                                     boolean enableUnstuck) {
    }
}
