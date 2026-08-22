package server.agents.administration;

public interface AgentCleanSlateResetPort {
    AgentCleanSlateTarget inspect(int characterId) throws Exception;

    void recordPreview(AgentCleanSlatePreview preview,
                       String requestedBy,
                       String reason,
                       String confirmationHash,
                       long previewedAtMs) throws Exception;

    AgentCleanSlateTarget resetGameplay(String resetId,
                                        int characterId,
                                        String expectedFingerprint,
                                        long executedAtMs) throws Exception;

    void markRejected(String resetId, String reason, long executedAtMs) throws Exception;

    void markCleanupWarning(String resetId, String warning) throws Exception;
}
