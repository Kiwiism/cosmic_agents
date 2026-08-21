package server.agents.progression.questwork;

import server.agents.runtime.decision.AgentDecisionAdvisor;
import server.agents.runtime.decision.AgentDecisionAssessment;
import server.agents.runtime.decision.AgentDecisionReasonCode;
import server.agents.runtime.decision.AgentDecisionRecommendation;
import server.agents.runtime.decision.AgentDecisionSignal;
import server.agents.runtime.decision.AgentDecisionSignalKind;
import server.agents.runtime.decision.AgentRecommendedAction;

import java.util.Comparator;

/** Deterministic fallback policy; callers decide whether to execute its advice. */
public final class AgentQuestStruggleAdvisor implements AgentDecisionAdvisor {
    private static final String TUNING_PREFIX =
            "server.agents.progression.questwork.AgentQuestStruggleAdvisor.";
    private static final long OBJECTIVE_PROGRESS_TIMEOUT_MS = tuningLong("OBJECTIVE_PROGRESS_TIMEOUT_MS");
    private static final long RELEVANT_DAMAGE_GRACE_MS = tuningLong("RELEVANT_DAMAGE_GRACE_MS");
    private static final long NAVIGATION_PROGRESS_GRACE_MS = tuningLong("NAVIGATION_PROGRESS_GRACE_MS");
    private static final int NAVIGATION_FAILURE_LIMIT = tuningInt("NAVIGATION_FAILURE_LIMIT");
    private static final int RETRY_LIMIT = tuningInt("RETRY_LIMIT");

    @Override
    public AgentDecisionRecommendation recommend(AgentDecisionAssessment assessment) {
        if (assessment == null) throw new IllegalArgumentException("quest assessment is required");
        if (has(assessment, AgentDecisionSignalKind.RESOURCE_PRESSURE)) {
            return recommendation(assessment, AgentRecommendedAction.RESUPPLY,
                    AgentDecisionReasonCode.RESOURCE_BUDGET_EXCEEDED,
                    "quest attempt resource budget was consumed");
        }
        if (maximum(assessment, AgentDecisionSignalKind.RECOVERABLE_FAILURE) >= RETRY_LIMIT) {
            return recommendation(assessment, AgentRecommendedAction.SUSPEND,
                    AgentDecisionReasonCode.RETRIES_EXHAUSTED,
                    "bounded local recovery attempts are exhausted");
        }
        if (has(assessment, AgentDecisionSignalKind.LEGITIMATE_WAIT)) {
            return recommendation(assessment, AgentRecommendedAction.CONTINUE,
                    AgentDecisionReasonCode.PROGRESS_CONTINUES,
                    "a declared warmup, spawn, script, or interaction wait is active");
        }
        if (maximum(assessment, AgentDecisionSignalKind.NAVIGATION_BLOCKED)
                >= NAVIGATION_FAILURE_LIMIT) {
            return recommendation(assessment, AgentRecommendedAction.REPLAN_CURRENT,
                    AgentDecisionReasonCode.NAVIGATION_BLOCKED,
                    "navigation failures require a new route or hunt map");
        }
        long nowMs = assessment.evaluatedAtMs();
        if (recent(assessment, AgentDecisionSignalKind.OBJECTIVE_ADVANCED,
                nowMs, OBJECTIVE_PROGRESS_TIMEOUT_MS)
                || recent(assessment, AgentDecisionSignalKind.RELEVANT_DAMAGE_OBSERVED,
                nowMs, RELEVANT_DAMAGE_GRACE_MS)
                || recent(assessment, AgentDecisionSignalKind.NAVIGATION_PROGRESS,
                nowMs, NAVIGATION_PROGRESS_GRACE_MS)
                || recent(assessment, AgentDecisionSignalKind.PROGRESS_OBSERVED,
                nowMs, OBJECTIVE_PROGRESS_TIMEOUT_MS)) {
            return recommendation(assessment, AgentRecommendedAction.CONTINUE,
                    AgentDecisionReasonCode.PROGRESS_CONTINUES,
                    "objective, relevant damage, navigation, or initial grace remains current");
        }
        return recommendation(assessment, AgentRecommendedAction.SUSPEND,
                AgentDecisionReasonCode.SAFE_BOUNDARY_REQUESTED,
                "no meaningful quest progress remains within the bounded attempt window");
    }

    private static AgentDecisionRecommendation recommendation(
            AgentDecisionAssessment assessment,
            AgentRecommendedAction action,
            AgentDecisionReasonCode reason,
            String explanation) {
        return AgentDecisionRecommendation.from(
                assessment, action, reason, "quest-struggle-fallback", "v1", explanation);
    }

    private static boolean has(
            AgentDecisionAssessment assessment,
            AgentDecisionSignalKind kind) {
        return assessment.signals().stream().anyMatch(signal -> signal.kind() == kind);
    }

    private static long maximum(
            AgentDecisionAssessment assessment,
            AgentDecisionSignalKind kind) {
        return assessment.signals().stream()
                .filter(signal -> signal.kind() == kind)
                .mapToLong(AgentDecisionSignal::value)
                .max().orElse(0L);
    }

    private static boolean recent(
            AgentDecisionAssessment assessment,
            AgentDecisionSignalKind kind,
            long nowMs,
            long graceMs) {
        return assessment.signals().stream()
                .filter(signal -> signal.kind() == kind)
                .max(Comparator.comparingLong(AgentDecisionSignal::observedAtMs))
                .map(signal -> nowMs - signal.observedAtMs() <= graceMs)
                .orElse(false);
    }

    private static int tuningInt(String name) {
        return config.AgentTuning.intValue(TUNING_PREFIX + name);
    }

    private static long tuningLong(String name) {
        return config.AgentTuning.longValue(TUNING_PREFIX + name);
    }
}
