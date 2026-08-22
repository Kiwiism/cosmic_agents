package server.agents.runtime.activity.delegation;

import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityKind;

/** Attaches and validates child work without invoking a second primary admission. */
public final class AgentDelegatedActivityCoordinator {
    public AgentDelegatedActivityLease attach(
            AgentRuntimeEntry entry,
            String leaseId,
            AgentActivityKind parentKind,
            String parentSessionId,
            AgentActivityKind childKind,
            String childSessionId,
            long nowMs,
            long deadlineMs,
            String purpose) {
        if (entry == null) throw new IllegalArgumentException("Agent entry is required");
        AgentDelegatedActivityLease lease = new AgentDelegatedActivityLease(
                1, leaseId, parentKind, parentSessionId, childKind, childSessionId,
                nowMs, deadlineMs, purpose);
        entry.capabilityStates().require(AgentDelegatedActivityState.STATE_KEY).attach(lease);
        return lease;
    }

    public boolean retainForParent(
            AgentRuntimeEntry entry,
            AgentActivityKind parentKind,
            String parentSessionId,
            long nowMs) {
        return entry != null && entry.capabilityStates()
                .require(AgentDelegatedActivityState.STATE_KEY)
                .retainForParent(parentKind, parentSessionId, nowMs);
    }

    public void release(AgentRuntimeEntry entry, String reason) {
        if (entry != null) {
            entry.capabilityStates().require(AgentDelegatedActivityState.STATE_KEY).release(reason);
        }
    }

    public boolean childOf(
            AgentRuntimeEntry entry,
            AgentActivityKind parentKind,
            AgentActivityKind childKind,
            long nowMs) {
        if (entry == null) return false;
        AgentDelegatedActivityState state = entry.capabilityStates()
                .require(AgentDelegatedActivityState.STATE_KEY);
        AgentDelegatedActivityLease lease = state.lease();
        return lease != null && lease.parentKind() == parentKind && lease.childKind() == childKind
                && state.retainForParent(parentKind, lease.parentSessionId(), nowMs);
    }

    public boolean childOf(
            AgentRuntimeEntry entry,
            AgentActivityKind parentKind,
            AgentActivityKind childKind) {
        if (entry == null) return false;
        AgentDelegatedActivityLease lease = entry.capabilityStates()
                .require(AgentDelegatedActivityState.STATE_KEY).lease();
        return lease != null && lease.parentKind() == parentKind && lease.childKind() == childKind;
    }
}
