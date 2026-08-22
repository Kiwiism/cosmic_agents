package server.agents.runtime.activity;

import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.List;

public record AgentActivityOwnershipReconciliation(
        Status status,
        AgentActivityKind expectedOwner,
        List<AgentActivityKind> retainedOwners,
        String reason) {

    public AgentActivityOwnershipReconciliation {
        retainedOwners = List.copyOf(retainedOwners == null ? List.of() : retainedOwners);
        reason = reason == null ? "" : reason.trim();
        if (status == null) throw new IllegalArgumentException("reconciliation status is required");
    }

    public boolean permitsExecution() {
        return status == Status.CLEAN || status == Status.RECONCILED;
    }

    public enum Status {
        CLEAN,
        RECONCILED,
        DRAINING,
        BLOCKED
    }
}
