package server.agents.runtime;

import server.agents.capabilities.movement.AgentIdlePhysicsService;
import server.agents.capabilities.movement.AgentFootholdIndexService;
import server.agents.capabilities.movement.AgentGroundingService;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementOnlyMapChangeService;
import server.agents.capabilities.movement.AgentMovementOnlyTickService;
import server.agents.capabilities.movement.AgentMovementTickCoordinator;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.follow.AgentFollowIdleMovementService;
import server.agents.capabilities.recovery.AgentRecoveryTeleportCoordinator;

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
                AgentIdlePhysicsService::tickIdleEntry,
                (entry, agent) -> AgentShopStateRuntime.shopVisitPending(entry),
                AgentFollowMapSyncRuntime::syncFollowMap,
                followAnchorResolver::apply,
                (entry, agent, anchor) -> AgentRecoveryTeleportCoordinator.recoverGrindPartyTeleportDistance(
                        entry,
                        agent,
                        anchor,
                        config.teleportDistance(),
                        config.outOfBoundsTeleportDistance(),
                        config.grindPartyTeleportDistanceMultiplier()),
                (entry, agent, target) -> AgentRecoveryTeleportCoordinator.recoverTeleportDistance(
                        entry,
                        agent,
                        target,
                        config.teleportDistance(),
                        config.outOfBoundsTeleportDistance()),
                AgentMovementOnlyRuntime::handleMapChange,
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
                (entry, target, coreRunAiTick) -> AgentMovementTickCoordinator.stepMovementCore(
                        entry,
                        target,
                        coreRunAiTick,
                        config.enableUnstuck(),
                        config.stopDistance()));
    }

    private static boolean handleMapChange(AgentRuntimeEntry entry, Character agent) {
        return AgentMovementOnlyMapChangeService.handleMapChange(
                entry,
                agent,
                new AgentMovementOnlyMapChangeService.Hooks(
                        AgentFootholdIndexService::buildFhIndex,
                        AgentGroundingService::findGroundPoint,
                        AgentMovementPoseService::teleportTo,
                        AgentMovementStateResetService::resetEntryStateAfterTeleport,
                        AgentMovementBroadcastService::broadcastMovement,
                        (shopEntry, shopAgent) -> AgentShopService.onMapChange(
                                shopEntry, shopAgent, AgentInventoryGatewayRuntime.inventory()),
                        AgentManagerStatusRuntime::checkManagerStatus));
    }

    public record MovementOnlyConfig(int teleportDistance,
                                     int outOfBoundsTeleportDistance,
                                     int grindPartyTeleportDistanceMultiplier,
                                     int followDistance,
                                     int stopDistance,
                                     boolean enableUnstuck) {
    }
}
