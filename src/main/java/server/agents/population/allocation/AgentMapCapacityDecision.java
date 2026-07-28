package server.agents.population.allocation;

public record AgentMapCapacityDecision(
        AgentMapCapacityCandidate candidate,
        Reason reason) {

    public enum Reason {
        RETAIN_ELIGIBLE_CURRENT_MAP,
        HIGHEST_RANKED_BELOW_SOFT_CAPACITY,
        HIGHEST_RANKED_BELOW_HARD_CAPACITY
    }

    public AgentMapCapacityDecision {
        if (candidate == null || reason == null) {
            throw new IllegalArgumentException("Map capacity decision and reason are required");
        }
    }
}
