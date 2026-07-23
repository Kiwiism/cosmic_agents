package server.agents.runtime;

import server.agents.monitoring.AgentPerformanceMonitor;

import server.agents.capabilities.recovery.AgentRecoveryTeleportCoordinator;
import server.agents.capabilities.follow.AgentFollowMapSyncCoordinator;

import server.agents.capabilities.movement.AgentIdlePhysicsService;
import server.agents.capabilities.trade.AgentTradeWindowTickService;
import server.agents.capabilities.recovery.AgentRecoveryTickService;
import server.agents.plans.AgentPlanReattachmentRuntime;
import server.agents.plans.AgentForegroundPlanRuntime;
import server.agents.runtime.maintenance.AgentMaintenanceSupervisor;
import server.agents.capabilities.behavior.AgentCrowdRespiteRuntime;
import server.agents.capabilities.behavior.AgentPioRelaxerInterludeRuntime;

import client.Character;
import java.util.function.Consumer;

public final class AgentLiveTickGateRuntime {
    private AgentLiveTickGateRuntime() {
    }

    public static boolean tickLiveGates(AgentLiveTickGateService.Context context,
                                        boolean perf,
                                        Consumer<AgentRuntimeEntry> tickScriptTasks,
                                        Consumer<AgentRuntimeEntry> issueGrind,
                                        Consumer<AgentRuntimeEntry> issueFollow,
                                        int teleportDistance,
                                        int outOfBoundsTeleportDistance,
                                        int grindPartyTeleportDistanceMultiplier) {
        return AgentLiveTickGateService.tickLiveGates(
                context,
                hooks(
                        perf,
                        tickScriptTasks,
                        issueGrind,
                        issueFollow,
                        teleportDistance,
                        outOfBoundsTeleportDistance,
                        grindPartyTeleportDistanceMultiplier));
    }

    private static AgentLiveTickGateService.Hooks hooks(boolean perf,
                                                        Consumer<AgentRuntimeEntry> tickScriptTasks,
                                                        Consumer<AgentRuntimeEntry> issueGrind,
                                                        Consumer<AgentRuntimeEntry> issueFollow,
                                                        int teleportDistance,
                                                        int outOfBoundsTeleportDistance,
                                                        int grindPartyTeleportDistanceMultiplier) {
        return new AgentLiveTickGateService.Hooks(
                (entry, agent, leader, runAiTick) ->
                        AgentCommonTickRuntime.runCommonTickSystems(entry, agent, leader, runAiTick, tickScriptTasks),
                (entry, agent, runAiTick) -> {
                    long nowMs = System.currentTimeMillis();
                    return AgentPioRelaxerInterludeRuntime.tick(entry, agent, nowMs, runAiTick)
                            || AgentCrowdRespiteRuntime.tick(entry, agent, nowMs, runAiTick);
                },
                (entry, agent) -> tickObjectiveSupervision(entry, agent, System.currentTimeMillis()),
                (entry, agent) -> AgentForegroundPlanRuntime.tick(
                        entry, agent, System.currentTimeMillis()),
                (tradeEntry, tradeAgent) -> AgentTradeWindowTickService.tickIfTradeWindowOpen(
                        tradeEntry,
                        tradeAgent,
                        (physicsEntry, physicsAgent) -> tickTradePhysics(physicsEntry, physicsAgent, perf)),
                (idleEntry, idleAgent) -> AgentIdleModeTickService.tickIdleMode(
                        idleEntry,
                        idleAgent,
                        new AgentIdleModeTickService.Hooks((ignored, physicsAgent) ->
                                tickIdleEntry(idleEntry, physicsAgent, perf))),
                (recoveryEntry, recoveryAgent, recoveryFollowAnchor, recoveryTargetPos) -> AgentRecoveryTickService.tickRecovery(
                        recoveryEntry,
                        recoveryAgent,
                        recoveryFollowAnchor,
                        recoveryTargetPos,
                        new AgentRecoveryTickService.Hooks(
                                AgentFollowMapSyncCoordinator::syncFollowMap,
                                (entry, agent, anchor) -> AgentRecoveryTeleportCoordinator.recoverGrindPartyTeleportDistance(
                                        entry,
                                        agent,
                                        anchor,
                                        teleportDistance,
                                        outOfBoundsTeleportDistance,
                                        grindPartyTeleportDistanceMultiplier),
                                (entry, agent, targetPos) -> AgentRecoveryTeleportCoordinator.recoverTeleportDistance(
                                        entry,
                                        agent,
                                        targetPos,
                                        teleportDistance,
                                        outOfBoundsTeleportDistance))),
                (mapEntry, mapAgent) -> AgentTrackedMapChangeTickService.tickTrackedMapChange(
                        mapEntry,
                        mapAgent,
                        new AgentTrackedMapChangeTickService.Hooks((trackedEntry, trackedAgent) ->
                                tickTrackedMapChange(trackedEntry, trackedAgent, issueGrind, issueFollow, perf))));
    }

    static boolean tickObjectiveSupervision(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (AgentPlanReattachmentRuntime.reattachIfNeeded(entry, agent, nowMs)) {
            return true;
        }
        return AgentMaintenanceSupervisor.tickRuntime(entry, agent, nowMs);
    }

    private static void tickTradePhysics(AgentRuntimeEntry entry, Character agent, boolean perf) {
        if (!perf) {
            AgentIdlePhysicsService.tickPhysicsOnly(entry, agent);
            return;
        }
        long startedAt = System.nanoTime();
        try {
            AgentIdlePhysicsService.tickPhysicsOnly(entry, agent);
        } finally {
            AgentPerformanceMonitor.record("tick-trade-physics", System.nanoTime() - startedAt);
        }
    }

    private static boolean tickIdleEntry(AgentRuntimeEntry entry, Character agent, boolean perf) {
        if (!perf) {
            return AgentIdlePhysicsService.tickIdleEntry(entry, agent);
        }
        long startedAt = System.nanoTime();
        boolean consumed = AgentIdlePhysicsService.tickIdleEntry(entry, agent);
        AgentPerformanceMonitor.record("tick-idle", System.nanoTime() - startedAt);
        return consumed;
    }

    private static boolean tickTrackedMapChange(AgentRuntimeEntry entry,
                                                Character agent,
                                                Consumer<AgentRuntimeEntry> issueGrind,
                                                Consumer<AgentRuntimeEntry> issueFollow,
                                                boolean perf) {
        if (!perf) {
            return AgentMapTransitionRuntime.handleTrackedMapChange(entry, agent, issueGrind, issueFollow);
        }
        long startedAt = System.nanoTime();
        boolean changed = false;
        try {
            changed = AgentMapTransitionRuntime.handleTrackedMapChange(entry, agent, issueGrind, issueFollow);
        } finally {
            if (changed) {
                AgentPerformanceMonitor.record("tick-map-change", System.nanoTime() - startedAt);
            }
        }
        return changed;
    }
}
