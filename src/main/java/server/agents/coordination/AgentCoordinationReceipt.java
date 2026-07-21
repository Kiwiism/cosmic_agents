package server.agents.coordination;

/** Receiver acknowledgement or proposal disposition for one routed message. */
public record AgentCoordinationReceipt(
        long messageId,
        int agentCharacterId,
        AgentCoordinationDisposition disposition,
        long recordedAtMillis,
        String detail) {

    public AgentCoordinationReceipt {
        if (messageId <= 0 || agentCharacterId <= 0 || disposition == null || recordedAtMillis < 0) {
            throw new IllegalArgumentException("Valid coordination receipt is required");
        }
        detail = detail == null ? "" : detail;
    }
}
