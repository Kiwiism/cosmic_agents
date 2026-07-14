package server.agents.runtime.scheduler;

/** Proof that one exact Agent session generation reached a strong quiet point. */
public record AgentQuiescenceToken(AgentSessionId sessionId,
                                   long requestId,
                                   AgentQuiescenceReason reason,
                                   long completedAtMs) {
    public AgentQuiescenceToken {
        if (sessionId == null || reason == null) {
            throw new IllegalArgumentException("Agent quiescence token identity and reason are required");
        }
        if (requestId < 1L || completedAtMs < 0L) {
            throw new IllegalArgumentException("Agent quiescence token values are invalid");
        }
    }
}
