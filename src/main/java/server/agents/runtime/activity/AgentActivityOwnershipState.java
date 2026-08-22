package server.agents.runtime.activity;

import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.List;

/** Session-local startup safety gate and ownership diagnostics. */
public final class AgentActivityOwnershipState {
    public static final AgentCapabilityStateKey<AgentActivityOwnershipState> STATE_KEY =
            new AgentCapabilityStateKey<>("runtime.activity-ownership",
                    AgentActivityOwnershipState.class, AgentActivityOwnershipState::new);

    private AgentActivityOwnershipReconciliation.Status status =
            AgentActivityOwnershipReconciliation.Status.CLEAN;
    private AgentActivityKind expectedOwner;
    private List<AgentActivityKind> retainedOwners = List.of();
    private long assessedAtMs;
    private String reason = "";

    public synchronized void record(AgentActivityOwnershipReconciliation result, long nowMs) {
        status = result.status();
        expectedOwner = result.expectedOwner();
        retainedOwners = result.retainedOwners();
        assessedAtMs = nowMs;
        reason = result.reason();
    }

    public synchronized boolean permitsExecution() {
        return status == AgentActivityOwnershipReconciliation.Status.CLEAN
                || status == AgentActivityOwnershipReconciliation.Status.RECONCILED;
    }

    public synchronized AgentActivityOwnershipReconciliation snapshot() {
        return new AgentActivityOwnershipReconciliation(
                status, expectedOwner, retainedOwners, reason);
    }

    public synchronized long assessedAtMs() { return assessedAtMs; }
}
