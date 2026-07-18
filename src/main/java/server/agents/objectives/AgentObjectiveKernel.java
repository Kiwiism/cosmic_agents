package server.agents.objectives;

import server.agents.events.AgentDomainEvent;
import server.agents.events.AgentEventPriority;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSessionEventRuntime;

import java.util.Map;

/** Durable-intent kernel above capabilities; it never performs game mutations. */
public final class AgentObjectiveKernel {
    private AgentObjectiveKernel() {
    }

    public static void start(AgentRuntimeEntry entry, AgentObjectiveDefinition objective, long nowMs) {
        AgentObjectiveState state = entry.capabilityStates().require(AgentObjectiveState.STATE_KEY);
        synchronized (state) {
            if (state.active != null && !state.active.objectiveId().equals(objective.objectiveId())) {
                append(state, nowMs, state.active, AgentObjectiveStatus.SUPERSEDED,
                        "superseded by " + objective.objectiveId());
            }
            state.active = objective;
            append(state, nowMs, objective, AgentObjectiveStatus.ACTIVE, "objective started");
        }
        publish(entry, objective, AgentObjectiveStatus.ACTIVE, nowMs, "objective started");
    }

    public static boolean transition(AgentRuntimeEntry entry, String objectiveId,
                                     AgentObjectiveStatus status, String reason, long nowMs) {
        if (status == null || status == AgentObjectiveStatus.ACTIVE) {
            throw new IllegalArgumentException("A terminal objective status is required");
        }
        AgentObjectiveState state = entry.capabilityStates().require(AgentObjectiveState.STATE_KEY);
        AgentObjectiveDefinition completed;
        synchronized (state) {
            completed = state.active;
            if (completed == null || !completed.objectiveId().equals(objectiveId)) {
                return false;
            }
            append(state, nowMs, completed, status, reason);
            state.active = null;
        }
        publish(entry, completed, status, nowMs, reason);
        return true;
    }

    public static AgentObjectiveDefinition active(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentObjectiveState.STATE_KEY).active();
    }

    private static void append(AgentObjectiveState state, long nowMs,
                               AgentObjectiveDefinition objective, AgentObjectiveStatus status, String reason) {
        while (state.journal.size() >= AgentObjectiveState.MAX_JOURNAL) {
            state.journal.removeFirst();
        }
        state.journal.addLast(new AgentObjectiveJournalEntry(nowMs, objective.objectiveId(), status,
                reason == null ? "" : reason, objective.source(), objective.behaviorVersion(),
                objective.correlationId()));
    }

    private static void publish(AgentRuntimeEntry entry, AgentObjectiveDefinition objective,
                                AgentObjectiveStatus status, long nowMs, String reason) {
        int agentId = entry.bot() == null ? 0 : entry.bot().getId();
        if (agentId <= 0) {
            return;
        }
        AgentSessionEventRuntime.bus(entry).publish(new AgentDomainEvent(agentId, nowMs,
                        "objective." + status.name().toLowerCase(),
                        objective.objectiveId() + ':' + status,
                        Map.of("objectiveId", objective.objectiveId(), "objectiveType", objective.type(),
                                "reason", reason == null ? "" : reason, "source", objective.source().name(),
                                "behaviorVersion", objective.behaviorVersion(),
                                "correlationId", objective.correlationId())),
                status == AgentObjectiveStatus.BLOCKED || status == AgentObjectiveStatus.FAILED
                        ? AgentEventPriority.IMPORTANT : AgentEventPriority.NORMAL);
    }
}
