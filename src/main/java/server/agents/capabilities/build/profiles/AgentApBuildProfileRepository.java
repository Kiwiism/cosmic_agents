package server.agents.capabilities.build.profiles;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AgentApBuildProfileRepository {
    private static final String DEFAULT_RESOURCE = "/agents/profiles/ap-build-profiles.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final AgentApBuildProfileRepository DEFAULT = loadResource(DEFAULT_RESOURCE);

    private final Map<String, AgentApBuildProfile> profilesById;

    AgentApBuildProfileRepository(AgentApBuildProfileCatalog catalog) {
        Map<String, AgentApBuildProfile> indexed = new LinkedHashMap<>();
        for (AgentApBuildProfile profile : catalog.profiles()) {
            if (indexed.putIfAbsent(profile.profileId(), profile) != null) {
                throw new IllegalArgumentException("Duplicate AP build profile: " + profile.profileId());
            }
        }
        profilesById = Map.copyOf(indexed);
    }

    public static AgentApBuildProfileRepository defaultRepository() {
        return DEFAULT;
    }

    public Optional<AgentApBuildProfile> find(String profileId) {
        return Optional.ofNullable(profilesById.get(profileId));
    }

    public List<AgentApBuildProfile> all() {
        return List.copyOf(profilesById.values());
    }

    private static AgentApBuildProfileRepository loadResource(String resourcePath) {
        try (InputStream input = AgentApBuildProfileRepository.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Missing Agent AP build profiles: " + resourcePath);
            }
            return new AgentApBuildProfileRepository(MAPPER.readValue(input, AgentApBuildProfileCatalog.class));
        } catch (IOException failure) {
            throw new IllegalStateException("Could not load Agent AP build profiles: " + resourcePath, failure);
        }
    }
}
