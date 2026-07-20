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
        AgentObjectiveCheckpointRuntime.persist(entry, nowMs);
        publish(entry, objective, AgentObjectiveStatus.ACTIVE, nowMs, "objective started");
    }

    public static boolean transition(AgentRuntimeEntry entry, String objectiveId,
                                     AgentObjectiveStatus status, String reason, long nowMs) {
        if (status == null || status == AgentObjectiveStatus.ACTIVE
                || status == AgentObjectiveStatus.SUSPENDED || status == AgentObjectiveStatus.RESUMED) {
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
        AgentObjectiveCheckpointRuntime.persist(entry, nowMs);
        publish(entry, completed, status, nowMs, reason);
        return true;
    }

    public static AgentObjectiveDefinition active(AgentRuntimeEntry entry) {
        return entry.capabilityStates().require(AgentObjectiveState.STATE_KEY).active();
    }

    /** Suspends the foreground objective and starts a bounded maintenance objective. */
    public static boolean suspendFor(AgentRuntimeEntry entry,
                                     AgentObjectiveDefinition maintenance,
                                     String reason,
                                     long nowMs) {
        AgentObjectiveState state = entry.capabilityStates().require(AgentObjectiveState.STATE_KEY);
        AgentObjectiveDefinition suspended;
        synchronized (state) {
            suspended = state.active;
            if (suspended == null || suspended.objectiveId().equals(maintenance.objectiveId())) {
                return false;
            }
            state.suspended.addFirst(new AgentObjectiveSuspension(suspended, reason, nowMs));
            append(state, nowMs, suspended, AgentObjectiveStatus.SUSPENDED, reason);
            state.active = maintenance;
            append(state, nowMs, maintenance, AgentObjectiveStatus.ACTIVE,
                    "maintenance started after suspending " + suspended.objectiveId());
        }
        AgentObjectiveCheckpointRuntime.persist(entry, nowMs);
        publish(entry, suspended, AgentObjectiveStatus.SUSPENDED, nowMs, reason);
        publish(entry, maintenance, AgentObjectiveStatus.ACTIVE, nowMs, reason);
        return true;
    }

    /** Completes maintenance and restores the most recently suspended objective. */
    public static boolean completeAndResume(AgentRuntimeEntry entry,
                                            String maintenanceObjectiveId,
                                            String reason,
                                            long nowMs) {
        return finishAndResume(entry, maintenanceObjectiveId, AgentObjectiveStatus.SUCCEEDED,
                reason, nowMs);
    }

    /** Terminates maintenance and restores the suspended foreground even when maintenance failed. */
    public static boolean finishAndResume(AgentRuntimeEntry entry,
                                          String maintenanceObjectiveId,
                                          AgentObjectiveStatus terminalStatus,
                                          String reason,
                                          long nowMs) {
        if (terminalStatus == null || terminalStatus == AgentObjectiveStatus.ACTIVE
                || terminalStatus == AgentObjectiveStatus.SUSPENDED
                || terminalStatus == AgentObjectiveStatus.RESUMED) {
            throw new IllegalArgumentException("A terminal maintenance status is required");
        }
        AgentObjectiveState state = entry.capabilityStates().require(AgentObjectiveState.STATE_KEY);
        AgentObjectiveDefinition maintenance;
        AgentObjectiveDefinition resumed;
        synchronized (state) {
            maintenance = state.active;
            if (maintenance == null || !maintenance.objectiveId().equals(maintenanceObjectiveId)) {
                return false;
            }
            append(state, nowMs, maintenance, terminalStatus, reason);
            AgentObjectiveSuspension suspension = state.suspended.pollFirst();
            resumed = suspension == null ? null : suspension.objective();
            state.active = resumed;
            if (resumed != null) {
                append(state, nowMs, resumed, AgentObjectiveStatus.RESUMED,
                        "resumed after " + maintenanceObjectiveId);
            }
        }
        AgentObjectiveCheckpointRuntime.persist(entry, nowMs);
        publish(entry, maintenance, terminalStatus, nowMs, reason);
        if (resumed != null) {
            publish(entry, resumed, AgentObjectiveStatus.RESUMED, nowMs,
                    "resumed after " + maintenanceObjectiveId);
        }
        return true;
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
