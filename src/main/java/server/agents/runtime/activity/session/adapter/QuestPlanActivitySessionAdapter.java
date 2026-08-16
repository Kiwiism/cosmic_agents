package server.agents.runtime.activity.session.adapter;

import client.Character;
import server.agents.plans.AgentPlanEntryRequest;
import server.agents.plans.AgentPlanExitRequest;
import server.agents.plans.AgentPlanExitResult;
import server.agents.plans.AgentPlanSessionHandle;
import server.agents.plans.AgentPlanSessionPhase;
import server.agents.plans.AgentPlanSessionResult;
import server.agents.plans.AgentPlanOutcome;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityAdmissionResult;
import server.agents.runtime.activity.session.AgentActivityExitResult;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivityOutcomePort;
import server.agents.runtime.activity.session.AgentActivityPhase;
import server.agents.runtime.activity.session.AgentActivitySessionSnapshot;
import server.agents.runtime.activity.session.AgentActivitySourcePort;
import server.agents.runtime.activity.session.AgentActivityTargetPort;
import server.agents.runtime.activity.session.AgentActivityTerminalOutcome;

import java.util.LinkedHashMap;
import java.util.Map;

/** Standard lifecycle adapter around one caller-owned universal plan. */
public final class QuestPlanActivitySessionAdapter
        implements AgentActivitySourcePort, AgentActivityTargetPort, AgentActivityOutcomePort {
    private final AgentRuntimeEntry entry;
    private final Character agent;
    private final AgentPlanEntryRequest request;

    public QuestPlanActivitySessionAdapter(
            AgentRuntimeEntry entry, Character agent, AgentPlanEntryRequest request) {
        this.entry = entry;
        this.agent = agent;
        this.request = request;
    }

    @Override
    public AgentActivitySessionSnapshot snapshot(long nowMs) {
        AgentPlanSessionHandle handle = AgentUniversalPlanRuntime.sessionHandle(entry);
        AgentPlanSessionPhase planPhase = AgentUniversalPlanRuntime.phase(entry);
        if (handle == null || !planPhase.ownsAgent()) {
            return AgentActivitySessionSnapshot.idle(
                    AgentActivityKind.QUEST_PLAN, agent == null ? "" : Integer.toString(agent.getId()));
        }
        return new AgentActivitySessionSnapshot(
                AgentActivityKind.QUEST_PLAN, phase(planPhase), handle.sessionId(),
                handle.requestId(), handle.callerId(), Integer.toString(handle.characterId()),
                handle.startedAtMs(), "");
    }

    @Override
    public AgentActivityExitResult requestGracefulExit(
            String reason, long nowMs, long deadlineMs) {
        AgentPlanSessionHandle handle = AgentUniversalPlanRuntime.sessionHandle(entry);
        if (handle == null || !AgentUniversalPlanRuntime.phase(entry).ownsAgent()) {
            return AgentActivityExitResult.released("quest plan is not active");
        }
        AgentPlanExitResult result = AgentUniversalPlanRuntime.requestExit(
                entry, agent, AgentPlanExitRequest.graceful(handle, reason, nowMs, deadlineMs));
        return switch (result.status()) {
            case REQUESTED, SUSPENDED -> AgentActivityExitResult.requested(result.reason());
            case EXITED, NOT_ACTIVE -> AgentActivityExitResult.released(result.reason());
            case REJECTED_NOT_OWNER, REJECTED_INVALID_REQUEST ->
                    AgentActivityExitResult.rejected(result.reason());
        };
    }

    @Override
    public AgentActivityAdmissionResult requestEntry(long nowMs) {
        if (request == null) {
            return AgentActivityAdmissionResult.rejected("quest adapter is not entry-bound");
        }
        AgentPlanSessionResult result = AgentUniversalPlanRuntime.requestSession(
                entry, agent, request, nowMs);
        return result.started() ? AgentActivityAdmissionResult.accepted(snapshot(nowMs))
                : AgentActivityAdmissionResult.rejected(result.reason());
    }

    private static AgentActivityPhase phase(AgentPlanSessionPhase phase) {
        return switch (phase) {
            case IDLE -> AgentActivityPhase.IDLE;
            case ACTIVE -> AgentActivityPhase.ACTIVE;
            case SUSPENDING -> AgentActivityPhase.SUSPENDING;
            case SUSPENDED -> AgentActivityPhase.SUSPENDED;
            case DRAINING -> AgentActivityPhase.DRAINING;
            case COMPLETED -> AgentActivityPhase.COMPLETED;
            case BLOCKED, FAILED -> AgentActivityPhase.FAILED;
            case CANCELLED -> AgentActivityPhase.CANCELLED;
        };
    }

    @Override
    public AgentActivityTerminalOutcome terminalOutcome(long nowMs) {
        AgentPlanOutcome outcome = AgentUniversalPlanRuntime.outcome(entry);
        if (outcome == null || outcome.handle() == null) return null;
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("planId", outcome.handle().planId());
        evidence.put("lastStepIndex", outcome.lastStepIndex());
        evidence.put("inputs", outcome.inputs());
        evidence.put("suggestedSuccessorPlanIds", outcome.suggestedSuccessorPlanIds());
        return new AgentActivityTerminalOutcome(
                AgentActivityKind.QUEST_PLAN, phase(outcome.phase()),
                outcome.handle().sessionId(), Integer.toString(outcome.handle().characterId()),
                outcome.reason(), outcome.retryable(), outcome.handle().startedAtMs(),
                outcome.endedAtMs(), evidence);
    }
}
