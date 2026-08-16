package server.agents.runtime.activity.session;

/** Read-only destination readiness check performed before a source activity is asked to exit. */
@FunctionalInterface
public interface AgentActivityPreflightPort {
    Result inspect(String agentId, AgentActivityKind targetKind, long nowMs);

    record Result(boolean ready, String reason) {
        public Result {
            reason = reason == null ? "" : reason.trim();
            if (!ready && reason.isBlank()) {
                throw new IllegalArgumentException("blocked preflight requires a reason");
            }
        }

        public static Result allowed() {
            return new Result(true, "");
        }

        public static Result blocked(String reason) {
            return new Result(false, reason);
        }
    }
}
