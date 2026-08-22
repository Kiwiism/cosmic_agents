package server.agents.runtime.activity.world;

/** Durable processing state kept separate from the immutable directive. */
public record AgentWorldDirectiveEnvelope(
        int schemaVersion,
        AgentWorldDirective directive,
        AgentWorldDirectiveStatus status,
        long claimedAtMs,
        long resolvedAtMs,
        long revision,
        String resolution) {

    public AgentWorldDirectiveEnvelope {
        resolution = resolution == null ? "" : resolution.trim();
        if (schemaVersion != 1 || directive == null || status == null || claimedAtMs < 0L
                || resolvedAtMs < 0L || revision < 1L) {
            throw new IllegalArgumentException("valid directive processing state is required");
        }
    }

    public static AgentWorldDirectiveEnvelope pending(AgentWorldDirective directive) {
        return new AgentWorldDirectiveEnvelope(1, directive, AgentWorldDirectiveStatus.PENDING,
                0L, 0L, 1L, "");
    }

    public AgentWorldDirectiveEnvelope claim(long nowMs) {
        if (status != AgentWorldDirectiveStatus.PENDING) {
            throw new IllegalStateException("only a pending directive can be claimed");
        }
        return new AgentWorldDirectiveEnvelope(schemaVersion, directive,
                AgentWorldDirectiveStatus.CLAIMED, nowMs, 0L, revision + 1L, "");
    }

    public AgentWorldDirectiveEnvelope resolve(
            AgentWorldDirectiveStatus terminalStatus, String reason, long nowMs) {
        if (terminalStatus == null || !terminalStatus.terminal() || status.terminal()) {
            throw new IllegalStateException("a non-terminal directive requires a terminal result");
        }
        return new AgentWorldDirectiveEnvelope(schemaVersion, directive, terminalStatus,
                claimedAtMs, nowMs, revision + 1L, reason);
    }
}
