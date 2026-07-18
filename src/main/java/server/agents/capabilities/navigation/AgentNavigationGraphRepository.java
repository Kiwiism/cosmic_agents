package server.agents.capabilities.navigation;

import server.agents.capabilities.movement.AgentMovementProfile;

import java.io.IOException;

/** Persistence boundary; graph construction and route selection do not own storage. */
public interface AgentNavigationGraphRepository {
    AgentNavigationGraph load(int mapId, AgentMovementProfile movementProfile)
            throws IOException, ClassNotFoundException;

    void save(AgentNavigationGraph graph) throws IOException;
}
