package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementPhysicsConfig;

import client.Character;
import server.bots.BotEntry;

import java.util.function.BiPredicate;
import java.util.function.Consumer;

public final class AgentTickCoreRuntime {
    private AgentTickCoreRuntime() {
    }

    public static void tickCore(BotEntry entry,
                                int leaderCharId,
                                int agentCharId,
                                Consumer<BotEntry> issueGrind,
                                Consumer<BotEntry> issueFollow) {
        tickCore(
                entry,
                leaderCharId,
                agentCharId,
                (runtimeEntry, runtimeLeaderCharId) ->
                        AgentLeaderSessionRuntime.resolveTickLeader(asBotEntry(runtimeEntry), runtimeLeaderCharId),
                (runtimeEntry, agent, leader, nowMs, runtimeLeaderCharId) ->
                        AgentLeaderSafetyRuntime.handleInactiveLeaderTick(
                                asBotEntry(runtimeEntry),
                                agent,
                                leader,
                                nowMs,
                                runtimeLeaderCharId),
                AgentMapTransitionRuntime::groundAfterMapChange,
                (runtimeEntry, agent, runAiTick) ->
                        AgentStandaloneMoveTargetRuntime.tickStandaloneMoveTarget(
                                asBotEntry(runtimeEntry),
                                agent,
                                runAiTick),
                (runtimeEntry, agent, leader) ->
                        AgentDeathTickRuntime.handleDeadTick(runtimeEntry, agent, leader),
                AgentTargetSnapshotRuntime::resolveFollowAnchor,
                AgentTargetSnapshotRuntime::captureTargetSnapshot,
                runtimeEntry -> AgentScriptTaskRuntime.tick(asBotEntry(runtimeEntry)),
                issueGrind,
                issueFollow,
                AgentLocalOpportunityAttackRuntime::tryLocalOpportunityAttackForLiveMode,
                AgentMovementTickRuntime::stepMovementCore,
                AgentAnchoredFarmRuntime::tickAnchoredFarm,
                (grindEntry, grindAgent, grindAgentPosition, grindTargetPosition, grindRunAiTick) ->
                        AgentGrindModeRuntime.tickGrindMode(
                                grindEntry,
                                grindAgent,
                                grindAgentPosition,
                                grindTargetPosition,
                                grindRunAiTick,
                                AgentMovementTickRuntime::stepMovementCore));
    }

    public static void tickCore(BotEntry entry,
                                int leaderCharId,
                                int agentCharId,
                                AgentTickCoreService.LeaderResolver leaderResolver,
                                AgentTickCoreService.InactiveLeaderTick inactiveLeaderTick,
                                BiPredicate<BotEntry, Character> groundAfterMapChange,
                                AgentOwnerlessTickService.OwnerlessMoveTick standaloneMoveTargetTick,
                                AgentTickCoreService.DeadTick deadTick,
                                AgentLiveTickContextService.FollowAnchorResolver followAnchorResolver,
                                AgentLiveTickContextService.TargetSnapshotCapture targetSnapshotCapture,
                                Consumer<BotEntry> tickScriptTasks,
                                Consumer<BotEntry> issueGrind,
                                Consumer<BotEntry> issueFollow,
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

    public static void tickCore(BotEntry entry,
                                int leaderCharId,
                                int agentCharId,
                                AgentTickCoreService.LeaderResolver leaderResolver,
                                AgentTickCoreService.InactiveLeaderTick inactiveLeaderTick,
                                BiPredicate<BotEntry, Character> groundAfterMapChange,
                                AgentOwnerlessTickService.OwnerlessMoveTick standaloneMoveTargetTick,
                                AgentTickCoreService.DeadTick deadTick,
                                AgentLiveTickContextService.FollowAnchorResolver followAnchorResolver,
                                AgentLiveTickContextService.TargetSnapshotCapture targetSnapshotCapture,
                                Consumer<BotEntry> tickScriptTasks,
                                Consumer<BotEntry> issueGrind,
                                Consumer<BotEntry> issueFollow,
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
                                                    BiPredicate<BotEntry, Character> groundAfterMapChange,
                                                    AgentOwnerlessTickService.OwnerlessMoveTick standaloneMoveTargetTick,
                                                    AgentTickCoreService.DeadTick deadTick,
                                                    AgentLiveTickContextService.FollowAnchorResolver followAnchorResolver,
                                                    AgentLiveTickContextService.TargetSnapshotCapture targetSnapshotCapture,
                                                    Consumer<BotEntry> tickScriptTasks,
                                                    Consumer<BotEntry> issueGrind,
                                                    Consumer<BotEntry> issueFollow,
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
                (runtimeEntry, agentCharId, nowMs) -> AgentTickPreflightRuntime.runPreflight(asBotEntry(runtimeEntry), agentCharId, nowMs),
                leaderResolver,
                inactiveLeaderTick,
                (ownerlessEntry, ownerlessAgent, ownerlessRunAiTick) -> AgentOwnerlessTickService.tickOwnerless(
                        asBotEntry(ownerlessEntry),
                        ownerlessAgent,
                        ownerlessRunAiTick,
                        (runtimeEntry, agent) -> groundAfterMapChange.test(asBotEntry(runtimeEntry), agent),
                        (runtimeEntry, agent, runAiTick) -> standaloneMoveTargetTick.tick(asBotEntry(runtimeEntry), agent, runAiTick),
                        () -> AgentIdlePhysicsRuntime.tickIdleEntry(asBotEntry(ownerlessEntry), ownerlessAgent)),
                deadTick,
                (liveEntry, liveAgent, liveLeader) -> AgentLiveTickContextRuntime.prepareLiveTickContext(
                        asBotEntry(liveEntry),
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
                                        asBotEntry(modeEntry),
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

    private static BotEntry asBotEntry(AgentRuntimeEntry entry) {
        return (BotEntry) entry;
    }
}
