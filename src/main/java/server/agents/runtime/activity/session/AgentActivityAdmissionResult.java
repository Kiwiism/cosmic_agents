package server.agents.runtime.activity.session;

/** Common admission result; system-specific results are mapped at adapters. */
public record AgentActivityAdmissionResult(
        Status status,
        AgentActivitySessionSnapshot session,
        String reason,
        long retryAtMs) {
    public AgentActivityAdmissionResult {
        if (status == null || retryAtMs < 0L) {
            throw new IllegalArgumentException("admission status and valid retry timing are required");
        }
        reason = reason == null ? "" : reason.trim();
        if (status == Status.ACCEPTED && (session == null || !session.phase().ownsAgent())) {
            throw new IllegalArgumentException("accepted admission requires an owning session");
        }
        if (status == Status.DEFERRED && retryAtMs <= 0L) {
            throw new IllegalArgumentException("deferred admission requires retry timing");
        }
    }

    public static AgentActivityAdmissionResult accepted(AgentActivitySessionSnapshot session) {
        return new AgentActivityAdmissionResult(Status.ACCEPTED, session, "", 0L);
    }

    public static AgentActivityAdmissionResult deferred(String reason, long retryAtMs) {
        return new AgentActivityAdmissionResult(Status.DEFERRED, null, reason, retryAtMs);
    }

    public static AgentActivityAdmissionResult rejected(String reason) {
        return new AgentActivityAdmissionResult(Status.REJECTED, null, reason, 0L);
    }

    public enum Status { ACCEPTED, DEFERRED, REJECTED }
}
