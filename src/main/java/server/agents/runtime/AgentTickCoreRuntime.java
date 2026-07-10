package server.agents.runtime;

import server.agents.plans.AgentScriptTaskCoordinator;
import server.agents.capabilities.movement.AgentIdlePhysicsService;
import server.agents.capabilities.movement.AgentOwnerlessTickService;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentMovementTickCoordinator;
import server.agents.capabilities.movement.AgentStandaloneMoveTargetCoordinator;
import server.agents.capabilities.combat.AgentDeathTickCoordinator;
import server.agents.capabilities.combat.AgentLocalOpportunityAttackCoordinator;
import server.agents.capabilities.combat.AgentLocalOpportunityAttackService;
import server.agents.capabilities.combat.AgentAnchoredFarmCoordinator;
import server.agents.capabilities.combat.AgentGrindModeCoordinator;
import server.agents.capabilities.combat.AgentGrindModeTickService;
import server.agents.capabilities.movement.AgentTargetSnapshot;

import client.Character;

import java.util.function.BiPredicate;
import java.util.function.Consumer;

public final class AgentTickCoreRuntime {
    private AgentTickCoreRuntime() {
    }

    public static void tickCore(AgentRuntimeEntry entry,
                                int leaderCharId,
                                int agentCharId,
                                Consumer<AgentRuntimeEntry> issueGrind,
                                Consumer<AgentRuntimeEntry> issueFollow) {
        tickCore(
                entry,
                leaderCharId,
                agentCharId,
                (runtimeEntry, runtimeLeaderCharId) ->
                        AgentLeaderSessionResolver.resolveTickLeader(runtimeEntry, runtimeLeaderCharId),
                (runtimeEntry, agent, leader, nowMs, runtimeLeaderCharId) ->
                        AgentLeaderSafetyCoordinator.handleInactiveLeaderTick(
                                runtimeEntry,
                                agent,
                                leader,
                                nowMs,
                                runtimeLeaderCharId),
                AgentMapTransitionRuntime::groundAfterMapChange,
                AgentStandaloneMoveTargetCoordinator::tickStandaloneMoveTarget,
                (runtimeEntry, agent, leader) ->
                        AgentDeathTickCoordinator.handleDeadTick(runtimeEntry, agent, leader),
                AgentTargetSnapshotCoordinator::resolveFollowAnchor,
                AgentTargetSnapshotCoordinator::captureTargetSnapshot,
                AgentScriptTaskCoordinator::tick,
                issueGrind,
                issueFollow,
                (attackEntry, attackAgent, attackAgentPosition, movementTargetPosition,
                 moveWindowReferencePosition, allowCombatMovement, allowJumpTowardTarget) -> {
                    AgentLocalOpportunityAttackService.Result result =
                            AgentLocalOpportunityAttackCoordinator.tryLocalOpportunityAttack(
                                    attackEntry,
                                    attackAgent,
                                    attackAgentPosition,
                                    movementTargetPosition,
                                    moveWindowReferencePosition,
                                    allowCombatMovement,
                                    allowJumpTowardTarget);
                    return new AgentLiveModeTickRuntime.LocalAttackResult(result.consumedTick(), result.targetPos());
                },
                AgentMovementTickCoordinator::stepMovementCore,
                AgentAnchoredFarmCoordinator::tickAnchoredFarm,
                (grindEntry, grindAgent, grindAgentPosition, grindTargetPosition, grindRunAiTick) -> {
                    AgentGrindModeTickService.Result result = AgentGrindModeCoordinator.tickGrindMode(
                            grindEntry,
                            grindAgent,
                            grindAgentPosition,
                            grindTargetPosition,
                            grindRunAiTick,
                            AgentMovementTickCoordinator::stepMovementCore);
                    return new AgentLiveModeTickRuntime.LocalAttackResult(result.consumedTick(), result.targetPos());
                });
    }

    public static void tickCore(AgentRuntimeEntry entry,
                                int leaderCharId,
                                int agentCharId,
                                AgentTickCoreService.LeaderResolver leaderResolver,
                                AgentTickCoreService.InactiveLeaderTick inactiveLeaderTick,
                                BiPredicate<AgentRuntimeEntry, Character> groundAfterMapChange,
                                AgentOwnerlessTickService.OwnerlessMoveTick standaloneMoveTargetTick,
                                AgentTickCoreService.DeadTick deadTick,
                                AgentLiveTickContextService.FollowAnchorResolver followAnchorResolver,
                                AgentLiveTickContextService.TargetSnapshotCapture targetSnapshotCapture,
                                Consumer<AgentRuntimeEntry> tickScriptTasks,
                                Consumer<AgentRuntimeEntry> issueGrind,
                                Consumer<AgentRuntimeEntry> issueFollow,
                                AgentLiveModeTickRuntime.LocalOpportunityAttack localOpportunityAttack,
                                AgentLiveModeTickRuntime.MovementCoreStep movementCoreStep,
                                AgentLiveModeTickRuntime.AnchoredFarmTick anchoredFarmTick,
                                AgentLiveModeTickRuntime.GrindModeTick grindModeTick) {
        tickCore(
                entry,
                leaderCharId,
                agentCharId,
                leaderResolver,
                inactiveLeaderTick,
                groundAfterMapChange,
                standaloneMoveTargetTick,
                deadTick,
                followAnchorResolver,
                targetSnapshotCapture,
                tickScriptTasks,
                issueGrind,
                issueFollow,
                localOpportunityAttack,
                movementCoreStep,
                anchoredFarmTick,
                grindModeTick,
                AgentMovementPhysicsConfig.configuredTeleportDist(),
                AgentMovementPhysicsConfig.configuredOutOfBoundsTeleportDist(),
                AgentRuntimeConfig.cfg.GRIND_PARTY_TELEPORT_DIST_MULTIPLIER,
                AgentMovementPhysicsConfig.configuredFollowDist());
    }

    public static void tickCore(AgentRuntimeEntry entry,
                                int leaderCharId,
                                int agentCharId,
                                AgentTickCoreService.LeaderResolver leaderResolver,
                                AgentTickCoreService.InactiveLeaderTick inactiveLeaderTick,
                                BiPredicate<AgentRuntimeEntry, Character> groundAfterMapChange,
                                AgentOwnerlessTickService.OwnerlessMoveTick standaloneMoveTargetTick,
                                AgentTickCoreService.DeadTick deadTick,
                                AgentLiveTickContextService.FollowAnchorResolver followAnchorResolver,
                                AgentLiveTickContextService.TargetSnapshotCapture targetSnapshotCapture,
                                Consumer<AgentRuntimeEntry> tickScriptTasks,
                                Consumer<AgentRuntimeEntry> issueGrind,
                                Consumer<AgentRuntimeEntry> issueFollow,
                                AgentLiveModeTickRuntime.LocalOpportunityAttack localOpportunityAttack,
                                AgentLiveModeTickRuntime.MovementCoreStep movementCoreStep,
                                AgentLiveModeTickRuntime.AnchoredFarmTick anchoredFarmTick,
                                AgentLiveModeTickRuntime.GrindModeTick grindModeTick,
                                int teleportDistance,
                                int outOfBoundsTeleportDistance,
                                int grindPartyTeleportDistanceMultiplier,
                                int followDistance) {
        AgentTickCoreService.tickCore(
                entry,
                leaderCharId,
                agentCharId,
                hooks(
                        leaderResolver,
                        inactiveLeaderTick,
                        groundAfterMapChange,
                        standaloneMoveTargetTick,
                        deadTick,
                        followAnchorResolver,
                        targetSnapshotCapture,
                        tickScriptTasks,
                        issueGrind,
                        issueFollow,
                        localOpportunityAttack,
                        movementCoreStep,
                        anchoredFarmTick,
                        grindModeTick,
                        teleportDistance,
                        outOfBoundsTeleportDistance,
                        grindPartyTeleportDistanceMultiplier,
                        followDistance));
    }

    private static AgentTickCoreService.Hooks hooks(AgentTickCoreService.LeaderResolver leaderResolver,
                                                    AgentTickCoreService.InactiveLeaderTick inactiveLeaderTick,
                                                    BiPredicate<AgentRuntimeEntry, Character> groundAfterMapChange,
                                                    AgentOwnerlessTickService.OwnerlessMoveTick standaloneMoveTargetTick,
                                                    AgentTickCoreService.DeadTick deadTick,
                                                    AgentLiveTickContextService.FollowAnchorResolver followAnchorResolver,
                                                    AgentLiveTickContextService.TargetSnapshotCapture targetSnapshotCapture,
                                                    Consumer<AgentRuntimeEntry> tickScriptTasks,
                                                    Consumer<AgentRuntimeEntry> issueGrind,
                                                    Consumer<AgentRuntimeEntry> issueFollow,
                                                    AgentLiveModeTickRuntime.LocalOpportunityAttack localOpportunityAttack,
                                                    AgentLiveModeTickRuntime.MovementCoreStep movementCoreStep,
                                                    AgentLiveModeTickRuntime.AnchoredFarmTick anchoredFarmTick,
                                                    AgentLiveModeTickRuntime.GrindModeTick grindModeTick,
                                                    int teleportDistance,
                                                    int outOfBoundsTeleportDistance,
                                                    int grindPartyTeleportDistanceMultiplier,
                                                    int followDistance) {
        return new AgentTickCoreService.Hooks(
                System::currentTimeMillis,
                AgentTickPreflightRuntime::runPreflight,
                leaderResolver,
                inactiveLeaderTick,
                (ownerlessEntry, ownerlessAgent, ownerlessRunAiTick) -> AgentOwnerlessTickService.tickOwnerless(
                        ownerlessEntry,
                        ownerlessAgent,
                        ownerlessRunAiTick,
                                groundAfterMapChange,
                                standaloneMoveTargetTick,
                        () -> AgentIdlePhysicsService.tickIdleEntry(ownerlessEntry, ownerlessAgent)),
                deadTick,
                (liveEntry, liveAgent, liveLeader) -> AgentLiveTickContextRuntime.prepareLiveTickContext(
                        liveEntry,
                        liveAgent,
                        liveLeader,
                        followAnchorResolver,
                        targetSnapshotCapture),
                AgentPerformanceMonitor::enabled,
                (gateEntry, gateAgent, gateLeader, gateFollowAnchor, liveContext, gateRunAiTick, perf) ->
                        AgentLiveTickGateRuntime.tickLiveGates(
                                new AgentLiveTickGateService.Context(
                                        gateEntry,
                                        gateAgent,
                                        gateLeader,
                                        gateFollowAnchor,
                                        liveContext.targetPosition(),
                                        gateRunAiTick),
                                perf,
                                tickScriptTasks,
                                issueGrind,
                                issueFollow,
                                teleportDistance,
                                outOfBoundsTeleportDistance,
                                grindPartyTeleportDistanceMultiplier),
                (modeEntry, modeAgent, modeFollowAnchor, liveContext, modeRunAiTick, nowMs, perf) ->
                        AgentLiveModeTickRuntime.tickLiveModes(
                                new AgentLiveModeTickService.Context(
                                        modeEntry,
                                        modeAgent,
                                        liveContext.agentPosition(),
                                        liveContext.targetPosition(),
                                        liveContext.targetSnapshot().followTargetPos(),
                                        modeFollowAnchor,
                                        modeRunAiTick,
                                        nowMs),
                                perf,
                                localOpportunityAttack,
                                movementCoreStep,
                                anchoredFarmTick,
                                grindModeTick,
                                followDistance));
    }
}
