package server.agents.progression;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

final class AgentVictoriaQuestHuntPolicyRepository {
    private static final String RESOURCE =
            "/agents/catalogs/victoria-quest-hunt-selection-policy.json";
    private static final AgentVictoriaQuestHuntPolicyRepository DEFAULT = load();

    private final AgentVictoriaQuestHuntPolicy policy;

    AgentVictoriaQuestHuntPolicyRepository(AgentVictoriaQuestHuntPolicy policy) {
        this.policy = policy;
    }

    static AgentVictoriaQuestHuntPolicyRepository defaultRepository() {
        return DEFAULT;
    }

    AgentVictoriaQuestHuntPolicy policy() {
        return policy;
    }

    private static AgentVictoriaQuestHuntPolicyRepository load() {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (InputStream input = AgentVictoriaQuestHuntPolicyRepository.class
                .getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing quest hunt selection policy: " + RESOURCE);
            }
            return new AgentVictoriaQuestHuntPolicyRepository(
                    mapper.readValue(input, AgentVictoriaQuestHuntPolicy.class));
        } catch (IOException failure) {
            throw new IllegalStateException("could not load quest hunt selection policy", failure);
        }
    }
}
