package server.agents.monitoring;

import server.agents.runtime.scheduler.AgentPriorityClass;
import server.agents.runtime.scheduler.AgentSessionId;
import server.agents.runtime.scheduler.AgentWorkClass;
import server.agents.runtime.simulation.AgentSimulationMode;

/** Immutable read-only view of one live central scheduler registration. */
public record AgentSchedulerRegistrationSnapshot(
        AgentSessionId sessionId,
        long nextDueMs,
        long estimatedCostNs,
        AgentWorkClass workClass,
        AgentPriorityClass priority,
        AgentSimulationMode simulationMode,
        boolean ready,
        boolean paused,
        boolean quiescent) {
    public AgentSchedulerRegistrationSnapshot {
        if (sessionId == null || workClass == null || priority == null || simulationMode == null) {
            throw new IllegalArgumentException("Agent scheduler registration snapshot is incomplete");
        }
        estimatedCostNs = Math.max(0L, estimatedCostNs);
    }

    public long overdueMs(long nowMs) {
        return Math.max(0L, nowMs - nextDueMs);
    }
}
