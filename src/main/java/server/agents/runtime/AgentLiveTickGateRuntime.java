package server.agents.runtime;

import client.Character;
import server.bots.BotEntry;
import java.util.function.Consumer;

public final class AgentLiveTickGateRuntime {
    private AgentLiveTickGateRuntime() {
    }

    public static boolean tickLiveGates(AgentLiveTickGateService.Context context,
                                        boolean perf,
                                        Consumer<BotEntry> tickScriptTasks,
                                        Consumer<BotEntry> issueGrind,
                                        Consumer<BotEntry> issueFollow,
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
                                                        Consumer<BotEntry> tickScriptTasks,
                                                        Consumer<BotEntry> issueGrind,
                                                        Consumer<BotEntry> issueFollow,
                                                        int teleportDistance,
                                                        int outOfBoundsTeleportDistance,
                                                        int grindPartyTeleportDistanceMultiplier) {
        return new AgentLiveTickGateService.Hooks(
                (entry, agent, leader, runAiTick) ->
                        AgentCommonTickRuntime.runCommonTickSystems(entry, agent, leader, runAiTick, tickScriptTasks),
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
                                AgentFollowMapSyncRuntime::syncFollowMap,
                                (entry, agent, anchor) -> AgentRecoveryTeleportRuntime.recoverGrindPartyTeleportDistance(
                                        entry,
                                        agent,
                                        anchor,
                                        teleportDistance,
                                        outOfBoundsTeleportDistance,
                                        grindPartyTeleportDistanceMultiplier),
                                (entry, agent, targetPos) -> AgentRecoveryTeleportRuntime.recoverTeleportDistance(
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

    private static void tickTradePhysics(BotEntry entry, Character agent, boolean perf) {
        if (!perf) {
            AgentIdlePhysicsRuntime.tickPhysicsOnly(entry, agent);
            return;
        }
        long startedAt = System.nanoTime();
        try {
            AgentIdlePhysicsRuntime.tickPhysicsOnly(entry, agent);
        } finally {
            AgentPerformanceMonitor.record("tick-trade-physics", System.nanoTime() - startedAt);
        }
    }

    private static boolean tickIdleEntry(BotEntry entry, Character agent, boolean perf) {
        if (!perf) {
            return AgentIdlePhysicsRuntime.tickIdleEntry(entry, agent);
        }
        long startedAt = System.nanoTime();
        boolean consumed = AgentIdlePhysicsRuntime.tickIdleEntry(entry, agent);
        AgentPerformanceMonitor.record("tick-idle", System.nanoTime() - startedAt);
        return consumed;
    }

    private static boolean tickTrackedMapChange(BotEntry entry,
                                                Character agent,
                                                Consumer<BotEntry> issueGrind,
                                                Consumer<BotEntry> issueFollow,
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
