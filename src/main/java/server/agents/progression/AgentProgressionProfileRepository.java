package server.agents.progression;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loads versioned progression personalities and provides stable first-time selection. */
public final class AgentProgressionProfileRepository {
    private static final String RESOURCE = "/agents/profiles/progression-profiles.json";
    private static final AgentProgressionProfileRepository DEFAULT = load();

    private final AgentProgressionProfileCatalog catalog;
    private final Map<String, AgentProgressionProfile> byId;

    AgentProgressionProfileRepository(AgentProgressionProfileCatalog catalog) {
        this.catalog = catalog;
        Map<String, AgentProgressionProfile> index = new LinkedHashMap<>();
        for (AgentProgressionProfile profile : catalog.profiles()) {
            if (index.putIfAbsent(profile.profileId(), profile) != null) {
                throw new IllegalArgumentException("duplicate progression profile " + profile.profileId());
            }
        }
        if (!index.containsKey(catalog.defaultProfileId())) {
            throw new IllegalArgumentException("unknown default progression profile "
                    + catalog.defaultProfileId());
        }
        byId = Map.copyOf(index);
    }

    public static AgentProgressionProfileRepository defaultRepository() {
        return DEFAULT;
    }

    public List<AgentProgressionProfile> all() {
        return catalog.profiles();
    }

    public AgentProgressionProfile defaultProfile() {
        return byId.get(catalog.defaultProfileId());
    }

    public Optional<AgentProgressionProfile> find(String profileId) {
        return Optional.ofNullable(byId.get(profileId));
    }

    public AgentProgressionProfile deterministicFor(int characterId) {
        List<AgentProgressionProfile> profiles = catalog.profiles();
        return profiles.get(Math.floorMod(characterId, profiles.size()));
    }

    private static AgentProgressionProfileRepository load() {
        try (InputStream input = AgentProgressionProfileRepository.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing progression profiles: " + RESOURCE);
            }
            return new AgentProgressionProfileRepository(
                    new ObjectMapper().readValue(input, AgentProgressionProfileCatalog.class));
        } catch (IOException failure) {
            throw new IllegalStateException("could not load progression profiles", failure);
        }
    }
}
