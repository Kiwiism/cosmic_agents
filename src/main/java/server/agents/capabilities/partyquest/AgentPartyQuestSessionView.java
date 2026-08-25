package server.agents.capabilities.partyquest;

/** Quest-neutral projection used by activity control without importing a PQ session aggregate. */
public record AgentPartyQuestSessionView(
        String questKey,
        String sessionId,
        Phase phase,
        int callerId,
        int memberCount,
        String mode,
        String failure,
        long startedAtMs,
        long lastProgressAtMs) {

    public enum Phase { ACTIVE, SUSPENDED, DRAINING, COMPLETED, FAILED }

    public AgentPartyQuestSessionView {
        questKey = AgentPartyQuestDefinition.normalize(questKey);
        if (sessionId == null || sessionId.isBlank() || phase == null || callerId <= 0
                || memberCount < 1 || startedAtMs < 0L || lastProgressAtMs < startedAtMs) {
            throw new IllegalArgumentException("valid party-quest session projection is required");
        }
        sessionId = sessionId.trim();
        mode = mode == null ? "" : mode.trim();
        failure = failure == null ? "" : failure.trim();
    }

    public boolean terminal() {
        return phase == Phase.COMPLETED || phase == Phase.FAILED;
    }
}
