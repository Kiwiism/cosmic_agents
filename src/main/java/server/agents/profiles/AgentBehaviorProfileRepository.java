package server.agents.profiles;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

public final class AgentBehaviorProfileRepository {
    public static final String MAPLE_ISLAND_QUESTER_ID = "maple-island-quester";
    private static final String MAPLE_ISLAND_QUESTER_RESOURCE =
            "/agents/profiles/maple-island-quester.profile.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final AgentBehaviorProfile MAPLE_ISLAND_QUESTER =
            load(MAPLE_ISLAND_QUESTER_RESOURCE);

    private AgentBehaviorProfileRepository() {
    }

    public static AgentBehaviorProfile mapleIslandQuester() {
        return MAPLE_ISLAND_QUESTER;
    }

    private static AgentBehaviorProfile load(String resourcePath) {
        try (InputStream input = AgentBehaviorProfileRepository.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Missing Agent behavior profile: " + resourcePath);
            }
            return MAPPER.readValue(input, AgentBehaviorProfile.class);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not load Agent behavior profile: " + resourcePath, failure);
        }
    }
}
