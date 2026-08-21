package server.agents.runtime.decision;

import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.List;

/** Evidence-backed advice. This value cannot mutate or transfer activity ownership. */
public record AgentDecisionRecommendation(
        AgentRecommendedAction action,
        AgentDecisionReasonCode reasonCode,
        long createdAtMs,
        String policyId,
        String policyVersion,
        String correlationId,
        String explanation,
        List<AgentDecisionSignal> evidence) {

    public AgentDecisionRecommendation {
        policyId = text(policyId);
        policyVersion = text(policyVersion);
        correlationId = text(correlationId);
        explanation = text(explanation);
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        if (action == null || reasonCode == null || createdAtMs < 0L
                || policyId.isEmpty() || policyVersion.isEmpty() || correlationId.isEmpty()
                || evidence.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("complete advisory recommendation provenance is required");
        }
    }

    public AgentActivityKind targetActivity() {
        return action.targetKind();
    }

    public static AgentDecisionRecommendation from(
            AgentDecisionAssessment assessment,
            AgentRecommendedAction action,
            AgentDecisionReasonCode reasonCode,
            String policyId,
            String policyVersion,
            String explanation) {
        if (assessment == null) {
            throw new IllegalArgumentException("decision assessment is required");
        }
        return new AgentDecisionRecommendation(action, reasonCode,
                assessment.evaluatedAtMs(), policyId, policyVersion,
                assessment.correlationId(), explanation, assessment.signals());
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
