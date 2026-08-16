package server.agents.runtime.activity.session;

/** Read-only common session view used by handoff and diagnostics. */
public record AgentActivitySessionSnapshot(
        AgentActivityKind kind,
        AgentActivityPhase phase,
        String sessionId,
        String requestId,
        String callerId,
        String agentId,
        long startedAtMs,
        String reason) {
    public AgentActivitySessionSnapshot {
        if (kind == null || phase == null || startedAtMs < 0L) {
            throw new IllegalArgumentException("activity kind, phase, and valid timing are required");
        }
        sessionId = normalize(sessionId);
        requestId = normalize(requestId);
        callerId = normalize(callerId);
        agentId = normalize(agentId);
        reason = normalize(reason);
        if (phase.ownsAgent() && (sessionId.isEmpty() || callerId.isEmpty() || agentId.isEmpty())) {
            throw new IllegalArgumentException("an owning activity requires session, caller, and Agent identity");
        }
    }

    public static AgentActivitySessionSnapshot idle(AgentActivityKind kind, String agentId) {
        return new AgentActivitySessionSnapshot(
                kind, AgentActivityPhase.IDLE, "", "", "", agentId, 0L, "");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
