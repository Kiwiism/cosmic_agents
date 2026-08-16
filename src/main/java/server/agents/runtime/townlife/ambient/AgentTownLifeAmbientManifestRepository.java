package server.agents.runtime.townlife.ambient;

import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.capabilities.townlife.AgentTownLifeProfile;
import server.agents.capabilities.townlife.AgentTownLifeProfileRepository;

import java.io.IOException;
import java.io.InputStream;

public final class AgentTownLifeAmbientManifestRepository {
    private static final String RESOURCE = "/agents/town-life/ambient-deployment.json";
    private static final AgentTownLifeAmbientManifest DEFAULT = load();

    private AgentTownLifeAmbientManifestRepository() {
    }

    public static AgentTownLifeAmbientManifest defaultManifest() {
        return DEFAULT;
    }

    private static AgentTownLifeAmbientManifest load() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream input = AgentTownLifeAmbientManifestRepository.class
                .getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing ambient TownLife deployment manifest");
            }
            AgentTownLifeAmbientManifest manifest = mapper.readValue(
                    input, AgentTownLifeAmbientManifest.class);
            for (AgentTownLifeAmbientManifest.Town town : manifest.towns()) {
                AgentTownLifeProfile profile = AgentTownLifeProfileRepository.defaultRepository()
                        .require(town.mapId());
                if (!profile.profileId().equals(town.profileId())) {
                    throw new IllegalArgumentException("ambient TownLife profile mismatch for map "
                            + town.mapId());
                }
                if (town.maxActive() > profile.admission().maxAmbientAgents()) {
                    throw new IllegalArgumentException("ambient TownLife allocation exceeds admission for "
                            + town.profileId());
                }
            }
            return manifest;
        } catch (IOException failure) {
            throw new IllegalStateException("could not load ambient TownLife deployment manifest", failure);
        }
    }
}
