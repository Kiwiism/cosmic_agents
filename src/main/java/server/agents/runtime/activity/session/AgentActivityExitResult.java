package server.agents.runtime.activity.session;

/** Result of asking an activity owner to release at a safe boundary. */
public record AgentActivityExitResult(Status status, String reason, long retryAtMs) {
    public AgentActivityExitResult {
        if (status == null || retryAtMs < 0L) {
            throw new IllegalArgumentException("exit status and valid retry timing are required");
        }
        reason = reason == null ? "" : reason.trim();
        if (status == Status.DEFERRED && retryAtMs <= 0L) {
            throw new IllegalArgumentException("deferred exit requires retry timing");
        }
    }

    public static AgentActivityExitResult requested(String reason) {
        return new AgentActivityExitResult(Status.REQUESTED, reason, 0L);
    }

    public static AgentActivityExitResult released(String reason) {
        return new AgentActivityExitResult(Status.RELEASED, reason, 0L);
    }

    public static AgentActivityExitResult deferred(String reason, long retryAtMs) {
        return new AgentActivityExitResult(Status.DEFERRED, reason, retryAtMs);
    }

    public static AgentActivityExitResult rejected(String reason) {
        return new AgentActivityExitResult(Status.REJECTED, reason, 0L);
    }

    public enum Status { REQUESTED, RELEASED, DEFERRED, REJECTED }
}
