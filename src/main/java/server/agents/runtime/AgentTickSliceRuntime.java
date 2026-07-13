package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementSettleService;
import server.agents.monitoring.AgentPerformanceMonitor;

import java.util.function.Consumer;

public final class AgentTickSliceRuntime {
    private AgentTickSliceRuntime() {
    }

    public static void tick(AgentRuntimeEntry entry,
                            int leaderCharId,
                            int agentCharId,
                            Consumer<AgentRuntimeEntry> issueGrind,
                            Consumer<AgentRuntimeEntry> issueFollow) {
        boolean monitorPerformance = AgentPerformanceMonitor.enabled();
        AgentTickSlicingService.TurnResult result = AgentTickSlicingService.runTurn(
                entry.tickSliceState(),
                new AgentTickSlicingService.Hooks(
                        () -> AgentMovementSettleService.beginTick(entry),
                        () -> AgentMailboxRuntime.drain(entry),
                        () -> AgentTickCoreRuntime.beginFrame(
                                entry,
                                leaderCharId,
                                agentCharId,
                                issueGrind,
                                issueFollow),
                        () -> AgentMovementSettleService.settleIfNeeded(entry),
                        () -> AgentTickFailurePolicy.resetFailures(entry),
                        failure -> AgentTickFailureRuntime.handleFailure(
                                entry,
                                leaderCharId,
                                agentCharId,
                                failure)));
        if (monitorPerformance && result.frameComplete() && !result.failed()) {
            AgentPerformanceMonitor.record("tick-total", result.frameExecutionNs());
        }
    }
}
