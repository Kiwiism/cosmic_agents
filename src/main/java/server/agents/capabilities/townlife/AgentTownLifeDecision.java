package server.agents.capabilities.townlife;

record AgentTownLifeDecision(
        AgentTownLifeState.Activity activity,
        String venueId,
        int targetAgentId,
        AgentTownLifeEncounterState.Type encounterType,
        String source,
        String correlationId) {

    AgentTownLifeDecision {
        venueId = venueId == null ? "" : venueId;
        source = source == null || source.isBlank() ? "default-policy" : source;
        correlationId = correlationId == null ? "" : correlationId;
    }

    static AgentTownLifeDecision deterministic(AgentTownLifeState.Activity activity) {
        return new AgentTownLifeDecision(activity, "", 0, defaultEncounter(activity),
                "default-policy", "");
    }

    static AgentTownLifeDecision deterministic(AgentTownLifeState.Activity activity, String venueId) {
        return new AgentTownLifeDecision(activity, venueId, 0, defaultEncounter(activity),
                "default-policy", "");
    }

    private static AgentTownLifeEncounterState.Type defaultEncounter(
            AgentTownLifeState.Activity activity) {
        if (activity == AgentTownLifeState.Activity.SOCIAL) {
            return AgentTownLifeEncounterState.Type.SOCIAL_CHAT;
        }
        return activity == AgentTownLifeState.Activity.WEAPON_FLOURISH
                ? AgentTownLifeEncounterState.Type.PLAYFUL_SPARRING : null;
    }
}
