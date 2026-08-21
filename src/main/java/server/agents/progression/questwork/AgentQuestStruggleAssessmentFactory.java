package server.agents.progression.questwork;

import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.decision.AgentDecisionAssessment;
import server.agents.runtime.decision.AgentDecisionSignal;
import server.agents.runtime.decision.AgentDecisionSignalKind;

import java.util.ArrayList;
import java.util.List;

/** Converts raw quest-attempt facts into portable evidence for any advisor. */
public final class AgentQuestStruggleAssessmentFactory {
    public AgentDecisionAssessment create(AgentQuestAttemptObservation observation) {
        if (observation == null) {
            throw new IllegalArgumentException("quest attempt observation is required");
        }
        List<AgentDecisionSignal> signals = new ArrayList<>();
        signals.add(signal(AgentDecisionSignalKind.PROGRESS_OBSERVED,
                observation.attemptStartedAtMs(), observation, "attempt-start", 0L,
                "timestamp-ms", "quest attempt began"));
        addEvent(signals, AgentDecisionSignalKind.OBJECTIVE_ADVANCED,
                observation.lastObjectiveProgressAtMs(), observation,
                "objective", "authoritative quest objective advanced");
        addEvent(signals, AgentDecisionSignalKind.RELEVANT_DAMAGE_OBSERVED,
                observation.lastRelevantDamageAtMs(), observation,
                "relevant-mob", "damage dealt to a mob relevant to remaining quest debt");
        addEvent(signals, AgentDecisionSignalKind.NAVIGATION_PROGRESS,
                observation.lastNavigationProgressAtMs(), observation,
                "route", "map, region, or position progress observed");
        if (observation.legitimateWaitUntilMs() > observation.observedAtMs()) {
            signals.add(signal(AgentDecisionSignalKind.LEGITIMATE_WAIT,
                    observation.observedAtMs(), observation, "wait-until",
                    observation.legitimateWaitUntilMs(), "timestamp-ms",
                    "known warmup, spawn, script, or interaction wait remains active"));
        }
        if (observation.navigationFailureCount() > 0) {
            signals.add(signal(AgentDecisionSignalKind.NAVIGATION_BLOCKED,
                    observation.observedAtMs(), observation, "navigation-failures",
                    observation.navigationFailureCount(), "count",
                    "bounded navigation failures in this quest attempt"));
        }
        if (observation.retryCount() > 0) {
            signals.add(signal(AgentDecisionSignalKind.RECOVERABLE_FAILURE,
                    observation.observedAtMs(), observation, "retries",
                    observation.retryCount(), "count",
                    "local recovery attempts already consumed"));
        }
        if (observation.resourceBudget() > 0
                && observation.resourceUnitsConsumed() >= observation.resourceBudget()) {
            signals.add(signal(AgentDecisionSignalKind.RESOURCE_PRESSURE,
                    observation.observedAtMs(), observation, "resource-budget",
                    observation.resourceUnitsConsumed(), "units",
                    "quest attempt resource budget=" + observation.resourceBudget()));
        }
        return new AgentDecisionAssessment(
                observation.agentId(), AgentActivityKind.QUESTING,
                observation.observedAtMs(), observation.workUnitId() + ':' + observation.observedAtMs(),
                signals);
    }

    private static void addEvent(
            List<AgentDecisionSignal> signals,
            AgentDecisionSignalKind kind,
            long timestamp,
            AgentQuestAttemptObservation observation,
            String subject,
            String detail) {
        if (timestamp >= 0L) {
            signals.add(signal(kind, timestamp, observation, subject,
                    0L, "", detail));
        }
    }

    private static AgentDecisionSignal signal(
            AgentDecisionSignalKind kind,
            long timestamp,
            AgentQuestAttemptObservation observation,
            String subject,
            long value,
            String unit,
            String detail) {
        return new AgentDecisionSignal(kind, timestamp, "quest-watchdog",
                "quest:" + observation.questId() + '/' + subject, value, unit, detail);
    }
}
