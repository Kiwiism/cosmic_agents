package server.agents.runtime.activity.session;

/** Caller-owned travel or relocation between two child activity sessions. */
@FunctionalInterface
public interface AgentActivityTransferPort {
    Result advance(long nowMs);

    record Result(Status status, String reason, long retryAtMs) {
        public Result {
            if (status == null || retryAtMs < 0L) {
                throw new IllegalArgumentException("transfer status and valid retry timing are required");
            }
            reason = reason == null ? "" : reason.trim();
            if (status == Status.PENDING && retryAtMs <= 0L) {
                throw new IllegalArgumentException("pending transfer requires retry timing");
            }
        }

        public static Result ready() { return new Result(Status.READY, "", 0L); }
        public static Result pending(String reason, long retryAtMs) {
            return new Result(Status.PENDING, reason, retryAtMs);
        }
        public static Result failed(String reason) {
            return new Result(Status.FAILED, reason, 0L);
        }

        public enum Status { READY, PENDING, FAILED }
    }
}
