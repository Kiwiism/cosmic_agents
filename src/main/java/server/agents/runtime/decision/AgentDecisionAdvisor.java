package server.agents.runtime.decision;

/** Replaceable advisory seam; deterministic policy remains the authoritative fallback. */
@FunctionalInterface
public interface AgentDecisionAdvisor {
    AgentDecisionRecommendation recommend(AgentDecisionAssessment assessment);
}
