package server.agents.runtime.scheduler;

import java.util.Set;

public record AgentLoadSheddingRecommendation(
        AgentLoadSheddingLevel level,
        Set<AgentLoadSheddingReason> reasons) {
    public AgentLoadSheddingRecommendation {
        if (level == null || reasons == null) {
            throw new IllegalArgumentException("Agent load-shedding recommendation is incomplete");
        }
        reasons = Set.copyOf(reasons);
    }
}
