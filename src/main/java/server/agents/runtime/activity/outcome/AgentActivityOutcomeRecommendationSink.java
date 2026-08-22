package server.agents.runtime.activity.outcome;

import server.agents.runtime.decision.AgentDecisionRecommendation;

@FunctionalInterface
public interface AgentActivityOutcomeRecommendationSink {
    void record(AgentDecisionRecommendation recommendation);
}
