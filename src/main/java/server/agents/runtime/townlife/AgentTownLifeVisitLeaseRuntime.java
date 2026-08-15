package server.agents.runtime.townlife;

import client.Character;
import server.agents.capabilities.townlife.AgentTownLifeExitRequest;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.capabilities.townlife.AgentTownLifeSessionResult;
import server.agents.runtime.AgentRuntimeEntry;

/** External lease coordinator that remains tickable while TownLife owns foreground execution. */
public final class AgentTownLifeVisitLeaseRuntime {
    private AgentTownLifeVisitLeaseRuntime() {
    }

    public static AgentTownLifeSessionResult start(
            AgentRuntimeEntry entry,
            Character agent,
            AgentTownLifeVisitLeaseRequest request,
            long nowMs,
            int identitySeed) {
        if (entry == null || agent == null || request == null) {
            return new AgentTownLifeSessionResult(
                    AgentTownLifeSessionResult.Status.REJECTED_INVALID_REQUEST, 0,
                    "entry, agent, and visit lease are required");
        }
        if (request.exitAtMs() <= nowMs) {
            throw new IllegalArgumentException("TownLife lease exit must be in the future");
        }
        AgentTownLifeSessionResult result = AgentTownLifeRuntime.requestSession(
                entry, agent, request.entryRequest(), request.admissionMode(), nowMs, identitySeed);
        AgentTownLifeVisitLeaseState leaseState = entry.capabilityStates()
                .require(AgentTownLifeVisitLeaseState.STATE_KEY);
        boolean initializeLease = result.status() == AgentTownLifeSessionResult.Status.STARTED
                || (result.status()
                == AgentTownLifeSessionResult.Status.ALREADY_ACTIVE_SAME_REQUEST
                && !leaseState.active());
        if (initializeLease && result.handle() != null) {
            leaseState.start(result.handle(), request.exitAtMs(), request.gracefulTimeoutMs(),
                    request.exitReason());
            AgentTownLifeVisitLeaseCheckpointRuntime.persist(entry, agent);
        }
        return result;
    }

    public static boolean active(AgentRuntimeEntry entry) {
        return entry != null && entry.capabilityStates()
                .find(AgentTownLifeVisitLeaseState.STATE_KEY)
                .map(AgentTownLifeVisitLeaseState::active).orElse(false);
    }

    /** Returns false so this external watcher never consumes foreground ownership. */
    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null) {
            return false;
        }
        AgentTownLifeVisitLeaseState lease = entry.capabilityStates()
                .require(AgentTownLifeVisitLeaseState.STATE_KEY);
        if (!lease.active()) {
            return false;
        }
        if (!AgentTownLifeRuntime.active(entry)) {
            clear(entry, agent);
            return false;
        }
        if (nowMs >= lease.exitAtMs()) {
            long deadline = nowMs + lease.gracefulTimeoutMs();
            AgentTownLifeRuntime.requestExit(entry, agent,
                    AgentTownLifeExitRequest.graceful(
                            lease.handle(), lease.exitReason(), nowMs, deadline));
        }
        return false;
    }

    public static void clear(AgentRuntimeEntry entry, Character agent) {
        if (entry != null) {
            entry.capabilityStates().require(AgentTownLifeVisitLeaseState.STATE_KEY).clear();
        }
        AgentTownLifeVisitLeaseCheckpointRuntime.delete(agent);
    }

    static void restore(
            AgentRuntimeEntry entry,
            Character agent,
            AgentTownLifeSessionHandleRestored restored) {
        entry.capabilityStates().require(AgentTownLifeVisitLeaseState.STATE_KEY)
                .start(restored.handle(), restored.exitAtMs(), restored.gracefulTimeoutMs(),
                        restored.exitReason());
    }

    record AgentTownLifeSessionHandleRestored(
            server.agents.capabilities.townlife.AgentTownLifeSessionHandle handle,
            long exitAtMs,
            long gracefulTimeoutMs,
            String exitReason) {
    }
}
