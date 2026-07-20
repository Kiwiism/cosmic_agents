package server.agents.personality;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loads the versioned semantic personality catalog. */
public final class AgentPersonalityProfileRepository {
    private static final String RESOURCE = "/agents/profiles/personality-profiles.json";
    private static final AgentPersonalityProfileRepository DEFAULT = load();

    private final AgentPersonalityProfileCatalog catalog;
    private final Map<String, AgentPersonalityProfile> byId;

    AgentPersonalityProfileRepository(AgentPersonalityProfileCatalog catalog) {
        this.catalog = catalog;
        Map<String, AgentPersonalityProfile> index = new LinkedHashMap<>();
        for (AgentPersonalityProfile profile : catalog.profiles()) {
            if (index.putIfAbsent(profile.profileId(), profile) != null) {
                throw new IllegalArgumentException("duplicate personality profile " + profile.profileId());
            }
        }
        if (!index.containsKey(catalog.defaultProfileId())) {
            throw new IllegalArgumentException("unknown default personality profile "
                    + catalog.defaultProfileId());
        }
        byId = Map.copyOf(index);
    }

    public static AgentPersonalityProfileRepository defaultRepository() {
        return DEFAULT;
    }

    public List<AgentPersonalityProfile> all() {
        return catalog.profiles();
    }

    public Optional<AgentPersonalityProfile> find(String profileId) {
        return Optional.ofNullable(byId.get(profileId));
    }

    public AgentPersonalityProfile defaultProfile() {
        return byId.get(catalog.defaultProfileId());
    }

    public AgentPersonalityProfile deterministicFor(int characterId) {
        List<AgentPersonalityProfile> profiles = catalog.profiles();
        return profiles.get(Math.floorMod(mix(characterId), profiles.size()));
    }

    private static int mix(int value) {
        int mixed = value ^ (value >>> 16);
        mixed *= 0x7feb352d;
        mixed ^= mixed >>> 15;
        mixed *= 0x846ca68b;
        return mixed ^ (mixed >>> 16);
    }

    private static AgentPersonalityProfileRepository load() {
        try (InputStream input = AgentPersonalityProfileRepository.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing personality profiles: " + RESOURCE);
            }
            return new AgentPersonalityProfileRepository(
                    new ObjectMapper().readValue(input, AgentPersonalityProfileCatalog.class));
        } catch (IOException failure) {
            throw new IllegalStateException("could not load personality profiles", failure);
        }
    }
}
