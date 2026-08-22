package server.agents.runtime.activity.delegation;

import server.agents.runtime.activity.session.AgentActivityKind;

/** A child capability executed under, but never competing with, a primary owner. */
public record AgentDelegatedActivityLease(
        int schemaVersion,
        String leaseId,
        AgentActivityKind parentKind,
        String parentSessionId,
        AgentActivityKind childKind,
        String childSessionId,
        long startedAtMs,
        long deadlineMs,
        String purpose) {

    public AgentDelegatedActivityLease {
        leaseId = required(leaseId, "delegation lease id");
        parentSessionId = required(parentSessionId, "parent session id");
        childSessionId = required(childSessionId, "child session id");
        purpose = purpose == null ? "" : purpose.trim();
        if (schemaVersion != 1 || parentKind == null || childKind == null
                || parentKind == childKind || startedAtMs < 0L
                || (deadlineMs > 0L && deadlineMs <= startedAtMs)) {
            throw new IllegalArgumentException("valid delegated activity lease is required");
        }
    }

    public boolean expiredAt(long nowMs) {
        return deadlineMs > 0L && nowMs >= deadlineMs;
    }

    private static String required(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " is required");
        return normalized;
    }
}
