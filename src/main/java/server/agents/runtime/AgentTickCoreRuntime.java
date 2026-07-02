package server.agents.runtime;

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
                AgentTickPreflightRuntime::runPreflight,
                leaderResolver,
                inactiveLeaderTick,
                (ownerlessEntry, ownerlessAgent, ownerlessRunAiTick) -> AgentOwnerlessTickService.tickOwnerless(
                        ownerlessEntry,
                        ownerlessAgent,
                        ownerlessRunAiTick,
                        groundAfterMapChange,
                        standaloneMoveTargetTick,
                        () -> AgentIdlePhysicsRuntime.tickIdleEntry(ownerlessEntry, ownerlessAgent)),
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
