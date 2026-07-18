package server.agents.capabilities.navigation;

import server.agents.capabilities.movement.AgentMovementProfile;

import java.io.IOException;
import java.nio.file.Path;

/** Versioned file implementation of the navigation graph repository. */
public final class FileAgentNavigationGraphRepository implements AgentNavigationGraphRepository {
    private final Path cacheDirectory;
    private final int graphVersion;

    public FileAgentNavigationGraphRepository(Path cacheDirectory, int graphVersion) {
        if (cacheDirectory == null || graphVersion <= 0) {
            throw new IllegalArgumentException("Navigation cache directory and graph version are required");
        }
        this.cacheDirectory = cacheDirectory;
        this.graphVersion = graphVersion;
    }

    @Override
    public AgentNavigationGraph load(int mapId, AgentMovementProfile movementProfile)
            throws IOException, ClassNotFoundException {
        AgentMovementProfile profile = movementProfile == null ? AgentMovementProfile.base() : movementProfile;
        return AgentNavigationGraphCacheFile.read(file(mapId, profile), graphVersion, mapId, profile);
    }

    @Override
    public void save(AgentNavigationGraph graph) throws IOException {
        AgentNavigationGraphCacheFile.write(file(graph.mapId, graph.movementProfile), graph);
    }

    Path file(int mapId, AgentMovementProfile movementProfile) {
        AgentMovementProfile profile = movementProfile == null ? AgentMovementProfile.base() : movementProfile;
        return cacheDirectory.resolve(mapId + "-s" + profile.totalSpeedStat()
                + "-j" + profile.totalJumpStat() + ".bin");
    }

    Path cacheDirectory() {
        return cacheDirectory;
    }
}
