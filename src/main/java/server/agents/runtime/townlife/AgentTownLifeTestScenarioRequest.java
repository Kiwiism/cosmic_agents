package server.agents.runtime.townlife;

/** Immutable external schedule for a repeatable TownLife enter/exit observation test. */
public record AgentTownLifeTestScenarioRequest(
        String scenarioId,
        String callerId,
        int townMapId,
        long visitDurationMs,
        long outsideDurationMs,
        long gracefulTimeoutMs,
        int cycles,
        AgentTownLifeStandbyTarget standbyTarget) {

    public AgentTownLifeTestScenarioRequest {
        scenarioId = normalize(scenarioId);
        callerId = normalize(callerId);
        standbyTarget = standbyTarget == null
                ? AgentTownLifeStandbyTarget.fallback() : standbyTarget;
        if (scenarioId.isBlank() || callerId.isBlank() || townMapId <= 0
                || visitDurationMs <= 0L || outsideDurationMs < 0L
                || gracefulTimeoutMs <= 0L || cycles <= 0) {
            throw new IllegalArgumentException("valid TownLife test scenario is required");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
