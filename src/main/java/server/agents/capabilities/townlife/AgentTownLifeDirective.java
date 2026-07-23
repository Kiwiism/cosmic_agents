package server.agents.capabilities.townlife;

/**
 * Safe high-level controller output. It deliberately contains no coordinates,
 * packets, live map objects, or mutation authority.
 */
public record AgentTownLifeDirective(
        AgentTownLifeState.Activity activity,
        String venueId,
        int targetAgentId,
        AgentTownLifeEncounterState.Type encounterType,
        long validUntilMs,
        String source,
        String correlationId) {

    public AgentTownLifeDirective {
        if (activity == null || activity == AgentTownLifeState.Activity.NONE || validUntilMs < 0) {
            throw new IllegalArgumentException("a concrete TownLife activity and validity are required");
        }
        venueId = normalize(venueId);
        source = normalize(source);
        correlationId = normalize(correlationId);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
