package server.agents.runtime.field;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

/** External deadline watcher that never consumes foreground execution. */
public final class AgentFieldVisitLeaseRuntime {
    private AgentFieldVisitLeaseRuntime() {
    }

    public static AgentFieldSessionResult start(
            AgentRuntimeEntry entry, Character agent, AgentFieldVisitLeaseRequest request, long nowMs) {
        if (entry == null || agent == null || request == null) {
            return new AgentFieldSessionResult(
                    AgentFieldSessionResult.Status.REJECTED_INVALID_REQUEST, null,
                    "entry, Agent, and field visit lease are required");
        }
        if (request.exitAtMs() <= nowMs) {
            throw new IllegalArgumentException("field lease exit must be in the future");
        }
        AgentFieldSessionResult result = AgentFieldActivityRuntime.requestSession(
                entry, agent, request.entryRequest(), request.admissionMode(), nowMs);
        AgentFieldVisitLeaseState state = entry.capabilityStates()
                .require(AgentFieldVisitLeaseState.STATE_KEY);
        boolean initialize = result.status() == AgentFieldSessionResult.Status.STARTED
                || result.status() == AgentFieldSessionResult.Status.ALREADY_ACTIVE_SAME_REQUEST
                && !state.active();
        if (initialize && result.handle() != null) {
            state.start(result.handle(), request.exitAtMs(), request.gracefulTimeoutMs(),
                    request.exitReason());
            AgentFieldVisitLeaseCheckpointRuntime.persist(entry, agent);
        }
        return result;
    }

    public static boolean active(AgentRuntimeEntry entry) {
        return entry != null && entry.capabilityStates()
                .find(AgentFieldVisitLeaseState.STATE_KEY)
                .map(AgentFieldVisitLeaseState::active).orElse(false);
    }

    /** Returns false because the activity beneath this watcher owns the actual tick. */
    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null) return false;
        AgentFieldVisitLeaseState lease = entry.capabilityStates()
                .require(AgentFieldVisitLeaseState.STATE_KEY);
        if (!lease.active()) return false;
        if (!AgentFieldActivityRuntime.active(entry)) {
            clear(entry, agent);
            return false;
        }
        if (nowMs >= lease.exitAtMs() && !lease.exitRequested()) {
            lease.markExitRequested();
            AgentFieldActivityRuntime.requestExit(entry, agent, AgentFieldExitRequest.graceful(
                    lease.handle(), lease.exitReason(), nowMs,
                    nowMs + lease.gracefulTimeoutMs()));
            AgentFieldVisitLeaseCheckpointRuntime.persist(entry, agent);
        }
        return false;
    }

    public static void clear(AgentRuntimeEntry entry, Character agent) {
        if (entry != null) {
            entry.capabilityStates().require(AgentFieldVisitLeaseState.STATE_KEY).clear();
        }
        AgentFieldVisitLeaseCheckpointRuntime.delete(agent);
    }

    static void restore(AgentRuntimeEntry entry, Restored restored) {
        AgentFieldVisitLeaseState state = entry.capabilityStates()
                .require(AgentFieldVisitLeaseState.STATE_KEY);
        state.start(restored.handle(), restored.exitAtMs(), restored.gracefulTimeoutMs(),
                restored.exitReason());
        if (restored.exitRequested()) state.markExitRequested();
    }

    record Restored(AgentFieldSessionHandle handle, long exitAtMs,
                    long gracefulTimeoutMs, String exitReason, boolean exitRequested) {
    }
}
