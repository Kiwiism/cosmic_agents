package server.agents.runtime.activity.session;

/** Resumes the exact source session when a handoff fails after ownership was released. */
@FunctionalInterface
public interface AgentActivityRollbackPort {
    Result requestResume(String sourceSessionId, long nowMs);

    record Result(Status status, String reason, long retryAtMs) {
        public Result {
            reason = reason == null ? "" : reason.trim();
            if (status == null || retryAtMs < 0L) {
                throw new IllegalArgumentException("valid rollback result is required");
            }
        }

        public static Result resumed(String reason) {
            return new Result(Status.RESUMED, reason, 0L);
        }

        public static Result deferred(String reason, long retryAtMs) {
            return new Result(Status.DEFERRED, reason, retryAtMs);
        }

        public static Result rejected(String reason) {
            return new Result(Status.REJECTED, reason, 0L);
        }

        public enum Status { RESUMED, DEFERRED, REJECTED }
    }
}
