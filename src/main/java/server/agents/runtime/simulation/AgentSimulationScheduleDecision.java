package server.agents.runtime.simulation;

import server.agents.runtime.scheduler.AgentPriorityClass;
import server.agents.runtime.scheduler.AgentWorkClass;

public record AgentSimulationScheduleDecision(
        AgentSimulationMode mode,
        long periodMs,
        AgentWorkClass workClass,
        AgentPriorityClass priority) {
    public AgentSimulationScheduleDecision {
        if (mode == null || workClass == null || priority == null) {
            throw new IllegalArgumentException("Agent simulation schedule decision is incomplete");
        }
        if (periodMs < 1L) {
            throw new IllegalArgumentException("Agent simulation cadence must be positive");
        }
    }
}
