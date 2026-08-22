package server.agents.runtime.activity.outcome;

import server.agents.runtime.activity.session.AgentActivityPhase;
import server.agents.runtime.activity.session.AgentActivityTerminalOutcome;
import server.agents.runtime.decision.AgentDecisionAssessment;
import server.agents.runtime.decision.AgentDecisionReasonCode;
import server.agents.runtime.decision.AgentDecisionRecommendation;
import server.agents.runtime.decision.AgentDecisionSignal;
import server.agents.runtime.decision.AgentDecisionSignalKind;
import server.agents.runtime.decision.AgentRecommendedAction;

import java.util.List;

/** Deterministic fallback advice; execution remains the World Director's responsibility. */
public final class AgentActivityOutcomeRecoveryPolicy {
    public static final String POLICY_ID = "activity-outcome-recovery";
    public static final String POLICY_VERSION = "v1";

    public AgentDecisionRecommendation recommend(
            String outcomeId, AgentActivityTerminalOutcome outcome, long nowMs) {
        if (outcome == null || outcomeId == null || outcomeId.isBlank()) {
            throw new IllegalArgumentException("outcome identity and terminal outcome are required");
        }
        AgentDecisionSignalKind signalKind = outcome.phase() == AgentActivityPhase.COMPLETED
                ? AgentDecisionSignalKind.OBJECTIVE_ADVANCED
                : outcome.retryable() ? AgentDecisionSignalKind.RECOVERABLE_FAILURE
                : AgentDecisionSignalKind.TERMINAL_FAILURE;
        AgentDecisionAssessment assessment = new AgentDecisionAssessment(
                outcome.agentId(), outcome.kind(), nowMs, outcomeId,
                List.of(AgentDecisionSignal.observed(signalKind, nowMs,
                        "activity-outcome", outcome.sessionId(), outcome.reason())));
        if (outcome.phase() == AgentActivityPhase.COMPLETED) {
            return AgentDecisionRecommendation.from(assessment, AgentRecommendedAction.CONTINUE,
                    AgentDecisionReasonCode.PROGRESS_CONTINUES, POLICY_ID, POLICY_VERSION,
                    "activity completed; request the next World Director decision");
        }
        if (outcome.retryable()) {
            return AgentDecisionRecommendation.from(assessment, AgentRecommendedAction.RETRY_LOCAL,
                    AgentDecisionReasonCode.RECOVERABLE_FAILURE, POLICY_ID, POLICY_VERSION,
                    "activity reported a retryable terminal outcome");
        }
        AgentRecommendedAction action = outcome.phase() == AgentActivityPhase.CANCELLED
                ? AgentRecommendedAction.SUSPEND : AgentRecommendedAction.SAFE_FALLBACK;
        AgentDecisionReasonCode reason = outcome.phase() == AgentActivityPhase.CANCELLED
                ? AgentDecisionReasonCode.SAFE_BOUNDARY_REQUESTED
                : AgentDecisionReasonCode.TERMINAL_FAILURE;
        return AgentDecisionRecommendation.from(assessment, action, reason,
                POLICY_ID, POLICY_VERSION, outcome.reason());
    }
}
