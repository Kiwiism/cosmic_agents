package server.agents.runtime;

import server.agents.capabilities.follow.AgentFollowIdleMovementService;

import client.Character;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.capabilities.shop.AgentShopStateRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;

import java.awt.Point;
import java.util.function.BiFunction;

public final class AgentMovementOnlyRuntime {
    private AgentMovementOnlyRuntime() {
    }

    public static void stepMovementOnly(AgentRuntimeEntry entry,
                                        Point targetPosition,
                                        boolean runAiTick,
                                        long nowMs,
                                        BiFunction<AgentRuntimeEntry, Character, Character> followAnchorResolver,
                                        MovementOnlyConfig config) {
        AgentMovementOnlyTickService.stepMovementOnly(
                entry,
                targetPosition,
                runAiTick,
                nowMs,
                hooks(followAnchorResolver, config));
    }

    private static AgentMovementOnlyTickService.MovementOnlyHooks hooks(
            BiFunction<AgentRuntimeEntry, Character, Character> followAnchorResolver,
            MovementOnlyConfig config) {
        return new AgentMovementOnlyTickService.MovementOnlyHooks(
                AgentIdlePhysicsRuntime::tickIdleEntry,
                (entry, agent) -> AgentShopStateRuntime.shopVisitPending(entry),
                AgentFollowMapSyncRuntime::syncFollowMap,
                followAnchorResolver::apply,
                (entry, agent, anchor) -> AgentRecoveryTeleportRuntime.recoverGrindPartyTeleportDistance(
                        entry,
                        agent,
                        anchor,
                        config.teleportDistance(),
                        config.outOfBoundsTeleportDistance(),
                        config.grindPartyTeleportDistanceMultiplier()),
                (entry, agent, target) -> AgentRecoveryTeleportRuntime.recoverTeleportDistance(
                        entry,
                        agent,
                        target,
                        config.teleportDistance(),
                        config.outOfBoundsTeleportDistance()),
                AgentMovementOnlyMapChangeRuntime::handleMapChange,
                (shopEntry, shopAgent) -> AgentShopService.tickShopVisit(
                        shopEntry, shopAgent, AgentInventoryGatewayRuntime.inventory()),
                AgentShopStateRuntime::activeShopTargetPosition,
                AgentShopStateRuntime::shopApproachDelayMs,
                (entry, agent, target, nowMs) -> AgentFollowIdleMovementService.tryFollowIdleMovementFastPath(
                        entry,
                        agent,
                        target,
                        nowMs,
                        config.followDistance(),
                        config.stopDistance()),
                (entry, target, coreRunAiTick) -> AgentMovementTickRuntime.stepMovementCore(
                        entry,
                        target,
                        coreRunAiTick,
                        config.enableUnstuck(),
                        config.stopDistance()));
    }

    public record MovementOnlyConfig(int teleportDistance,
                                     int outOfBoundsTeleportDistance,
                                     int grindPartyTeleportDistanceMultiplier,
                                     int followDistance,
                                     int stopDistance,
                                     boolean enableUnstuck) {
    }
}
