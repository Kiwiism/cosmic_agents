package server.agents.runtime.decision;

import server.agents.runtime.journey.AgentJourneyEvent;
import server.agents.runtime.journey.AgentJourneyEventDraft;
import server.agents.runtime.journey.AgentJourneyEventType;
import server.agents.runtime.journey.AgentJourneyJournalStore;

import java.util.Map;

/** Projects advisory output into the neutral journey ledger without executing it. */
public final class AgentDecisionJourneyRecorder {
    private final AgentJourneyJournalStore journal;

    public AgentDecisionJourneyRecorder(AgentJourneyJournalStore journal) {
        if (journal == null) throw new IllegalArgumentException("journey journal is required");
        this.journal = journal;
    }

    public AgentJourneyEvent record(
            int characterId,
            AgentDecisionAssessment assessment,
            AgentDecisionRecommendation recommendation) {
        if (assessment == null || recommendation == null
                || !assessment.correlationId().equals(recommendation.correlationId())) {
            throw new IllegalArgumentException("matching assessment and recommendation are required");
        }
        AgentJourneyEventType type = recovery(recommendation.action())
                ? AgentJourneyEventType.RECOVERY_RECOMMENDED
                : AgentJourneyEventType.DECISION_RECOMMENDED;
        return journal.append(new AgentJourneyEventDraft(
                "decision:" + assessment.correlationId(), assessment.agentId(), characterId,
                recommendation.createdAtMs(), type, assessment.currentActivity(),
                "decision-advisory", assessment.correlationId(), recommendation.explanation(),
                Map.of("action", recommendation.action().name(),
                        "reasonCode", recommendation.reasonCode().name(),
                        "policyId", recommendation.policyId(),
                        "policyVersion", recommendation.policyVersion(),
                        "evidenceCount", Integer.toString(recommendation.evidence().size()))));
    }

    private static boolean recovery(AgentRecommendedAction action) {
        return action == AgentRecommendedAction.RETRY_LOCAL
                || action == AgentRecommendedAction.REPLAN_CURRENT
                || action == AgentRecommendedAction.SUSPEND
                || action == AgentRecommendedAction.RESUPPLY
                || action == AgentRecommendedAction.ABANDON_OBJECTIVE
                || action == AgentRecommendedAction.SAFE_FALLBACK;
    }
}
