package server.agents.capabilities.townlife;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Loads one independently tunable resource file per supported town. */
public final class AgentTownLifeProfileRepository {
    private static final String ROOT = "/agents/town-life/";
    private static final AgentTownLifeProfileRepository DEFAULT = load();

    private final Map<Integer, AgentTownLifeProfile> byMapId;

    AgentTownLifeProfileRepository(List<AgentTownLifeProfile> profiles) {
        Map<Integer, AgentTownLifeProfile> index = new LinkedHashMap<>();
        for (AgentTownLifeProfile profile : profiles) {
            AgentTownLifeProfileValidator.requireValid(profile);
            if (index.putIfAbsent(profile.mapId(), profile) != null) {
                throw new IllegalArgumentException("duplicate town-life map " + profile.mapId());
            }
        }
        byMapId = Map.copyOf(index);
    }

    public static AgentTownLifeProfileRepository defaultRepository() {
        return DEFAULT;
    }

    public Optional<AgentTownLifeProfile> find(int mapId) {
        return Optional.ofNullable(byMapId.get(mapId));
    }

    public AgentTownLifeProfile require(int mapId) {
        return find(mapId).orElseThrow(() ->
                new IllegalArgumentException("no town-life profile for map " + mapId));
    }

    public List<AgentTownLifeProfile> profiles() {
        return List.copyOf(byMapId.values());
    }

    private static AgentTownLifeProfileRepository load() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream input = AgentTownLifeProfileRepository.class
                .getResourceAsStream(ROOT + "index.json")) {
            if (input == null) {
                throw new IllegalStateException("missing town-life profile index");
            }
            ProfileIndex index = mapper.readValue(input, ProfileIndex.class);
            List<AgentTownLifeProfile> profiles = index.profiles().stream()
                    .map(resource -> readProfile(mapper, resource))
                    .toList();
            return new AgentTownLifeProfileRepository(profiles);
        } catch (IOException failure) {
            throw new IllegalStateException("could not load town-life profiles", failure);
        }
    }

    private static AgentTownLifeProfile readProfile(ObjectMapper mapper, String resource) {
        try (InputStream input = AgentTownLifeProfileRepository.class
                .getResourceAsStream(ROOT + resource)) {
            if (input == null) {
                throw new IllegalStateException("missing town-life profile " + resource);
            }
            return mapper.readValue(input, AgentTownLifeProfile.class);
        } catch (IOException failure) {
            throw new IllegalStateException("could not load town-life profile " + resource, failure);
        }
    }

    private record ProfileIndex(List<String> profiles) {
        private ProfileIndex {
            profiles = List.copyOf(profiles == null ? List.of() : profiles);
        }
    }
}
