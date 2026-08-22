package server.agents.runtime.activity.delegation;

import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.state.AgentCapabilityStateKey;

/** Session-local single-child lease; durable child systems retain their own checkpoints. */
public final class AgentDelegatedActivityState {
    public static final AgentCapabilityStateKey<AgentDelegatedActivityState> STATE_KEY =
            new AgentCapabilityStateKey<>("runtime.delegated-activity",
                    AgentDelegatedActivityState.class, AgentDelegatedActivityState::new);

    private AgentDelegatedActivityLease lease;
    private long revision;
    private String terminalReason = "";

    public synchronized void attach(AgentDelegatedActivityLease requested) {
        if (requested == null) throw new IllegalArgumentException("delegation lease is required");
        if (lease != null && !lease.leaseId().equals(requested.leaseId())) {
            throw new IllegalStateException("another delegated activity is already attached");
        }
        if (!requested.equals(lease)) {
            lease = requested;
            terminalReason = "";
            revision++;
        }
    }

    public synchronized boolean retainForParent(
            AgentActivityKind parentKind, String parentSessionId, long nowMs) {
        if (lease == null) return false;
        String sessionId = parentSessionId == null ? "" : parentSessionId.trim();
        if (lease.expiredAt(nowMs) || lease.parentKind() != parentKind
                || !lease.parentSessionId().equals(sessionId)) {
            release("delegated activity lost its owning parent session");
            return false;
        }
        return true;
    }

    public synchronized void release(String reason) {
        if (lease == null) return;
        lease = null;
        terminalReason = reason == null ? "" : reason.trim();
        revision++;
    }

    public synchronized AgentDelegatedActivityLease lease() { return lease; }
    public synchronized boolean active() { return lease != null; }
    public synchronized long revision() { return revision; }
    public synchronized String terminalReason() { return terminalReason; }
}
