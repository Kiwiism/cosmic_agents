package server.agents.runtime.scheduler;

import java.util.Set;

public record AgentLoadSheddingState(
        AgentLoadSheddingLevel level,
        Set<AgentLoadSheddingReason> reasons,
        long sinceMs,
        long epoch) {
    public AgentLoadSheddingState {
        if (level == null || reasons == null) {
            throw new IllegalArgumentException("Agent load-shedding state is incomplete");
        }
        reasons = Set.copyOf(reasons);
        sinceMs = Math.max(0L, sinceMs);
        epoch = Math.max(0L, epoch);
    }

    public static AgentLoadSheddingState normal(long nowMs) {
        return new AgentLoadSheddingState(AgentLoadSheddingLevel.NORMAL, Set.of(), nowMs, 0L);
    }
}
