package server.agents.behavior;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only behavior policy catalog. Personality identity remains owned by personality. */
public final class AgentBehaviorPolicyRepository {
    private static final String RESOURCE = "/agents/profiles/personality-behavior-policies.json";
    private static final AgentBehaviorPolicyRepository DEFAULT = load();

    private final Map<String, AgentBehaviorPolicyProfile> byPersonality;
    private final AgentBehaviorPolicyProfile defaultPolicy;

    AgentBehaviorPolicyRepository(AgentBehaviorPolicyCatalog catalog) {
        Map<String, AgentBehaviorPolicyProfile> index = new LinkedHashMap<>();
        for (AgentBehaviorPolicyProfile policy : catalog.policies()) {
            if (index.putIfAbsent(policy.personalityProfileId(), policy) != null) {
                throw new IllegalArgumentException("duplicate behavior policy " + policy.personalityProfileId());
            }
        }
        defaultPolicy = index.get(catalog.defaultPersonalityProfileId());
        if (defaultPolicy == null) throw new IllegalArgumentException("unknown default behavior policy");
        byPersonality = Map.copyOf(index);
    }

    public static AgentBehaviorPolicyRepository defaultRepository() {
        return DEFAULT;
    }

    public AgentBehaviorPolicyProfile resolve(String personalityProfileId) {
        return byPersonality.getOrDefault(personalityProfileId, defaultPolicy);
    }

    private static AgentBehaviorPolicyRepository load() {
        try (InputStream input = AgentBehaviorPolicyRepository.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("missing behavior policies: " + RESOURCE);
            return new AgentBehaviorPolicyRepository(
                    new ObjectMapper().readValue(input, AgentBehaviorPolicyCatalog.class));
        } catch (IOException failure) {
            throw new IllegalStateException("could not load behavior policies", failure);
        }
    }
}
