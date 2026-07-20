package server.agents.progression;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

record AgentVictoriaProgressionPolicy(
        int schemaVersion,
        String policyId,
        boolean questingEnabledByDefault,
        int questDecisionPercent,
        int interactionDelayMinMs,
        int interactionDelayMaxMs) {

    private static final String RESOURCE = "/agents/catalogs/victoria-progression-policy.json";
    private static final AgentVictoriaProgressionPolicy DEFAULT = load();

    AgentVictoriaProgressionPolicy {
        if (schemaVersion <= 0 || policyId == null || policyId.isBlank()
                || questDecisionPercent < 0 || questDecisionPercent > 100
                || interactionDelayMinMs < 0 || interactionDelayMaxMs < interactionDelayMinMs) {
            throw new IllegalArgumentException("valid Victoria progression policy bounds are required");
        }
    }

    static AgentVictoriaProgressionPolicy defaultPolicy() {
        return DEFAULT;
    }

    long interactionDelayMs(int characterId, int questId, int stageSalt) {
        int width = interactionDelayMaxMs - interactionDelayMinMs + 1;
        int offset = Math.floorMod(characterId * 31 + questId * 17 + stageSalt * 13, width);
        return interactionDelayMinMs + offset;
    }

    private static AgentVictoriaProgressionPolicy load() {
        try (InputStream input = AgentVictoriaProgressionPolicy.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing Victoria progression policy: " + RESOURCE);
            }
            return new ObjectMapper().readValue(input, AgentVictoriaProgressionPolicy.class);
        } catch (IOException failure) {
            throw new IllegalStateException("could not load Victoria progression policy", failure);
        }
    }
}
