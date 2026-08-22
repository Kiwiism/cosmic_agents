package server.agents.runtime.activity.control.rollout;

public record AgentWorldDirectorRolloutGateResult(boolean permitted, String reason) {
    public AgentWorldDirectorRolloutGateResult {
        reason = reason == null ? "" : reason.trim();
        if (reason.isEmpty()) throw new IllegalArgumentException("rollout gate evidence is required");
    }

    public static AgentWorldDirectorRolloutGateResult allow(String reason) {
        return new AgentWorldDirectorRolloutGateResult(true, reason);
    }

    public static AgentWorldDirectorRolloutGateResult block(String reason) {
        return new AgentWorldDirectorRolloutGateResult(false, reason);
    }
}
