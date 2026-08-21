package server.agents.runtime.decision;

import server.agents.runtime.AgentRuntimeEntry;

/** Validates and records advice but deliberately owns no execution or handoff dependency. */
public final class AgentDecisionAdvisoryService {
    private final AgentDecisionAdvisor fallbackAdvisor;

    public AgentDecisionAdvisoryService(AgentDecisionAdvisor fallbackAdvisor) {
        if (fallbackAdvisor == null) {
            throw new IllegalArgumentException("deterministic fallback advisor is required");
        }
        this.fallbackAdvisor = fallbackAdvisor;
    }

    public AgentDecisionRecommendation evaluate(AgentDecisionAssessment assessment) {
        if (assessment == null) {
            throw new IllegalArgumentException("decision assessment is required");
        }
        AgentDecisionRecommendation recommendation = fallbackAdvisor.recommend(assessment);
        if (recommendation == null
                || !recommendation.correlationId().equals(assessment.correlationId())
                || recommendation.createdAtMs() != assessment.evaluatedAtMs()) {
            throw new IllegalStateException("advisor returned recommendation for a different evaluation");
        }
        return recommendation;
    }

    public AgentDecisionRecord record(
            AgentRuntimeEntry entry,
            AgentDecisionRecommendation recommendation) {
        if (entry == null || recommendation == null) {
            throw new IllegalArgumentException("Agent runtime and recommendation are required");
        }
        return entry.capabilityStates().require(AgentDecisionProvenanceState.STATE_KEY).record(
                recommendation.createdAtMs(),
                "activity-advisory",
                recommendation.action().name(),
                recommendation.policyId(),
                recommendation.policyVersion(),
                recommendation.reasonCode().name() + ": " + recommendation.explanation(),
                recommendation.correlationId(),
                recommendation.evidence().stream()
                        .map(signal -> signal.kind().name() + '@' + signal.source())
                        .toList());
    }
}
