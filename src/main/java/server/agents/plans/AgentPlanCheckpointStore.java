package server.agents.plans;

import java.io.IOException;
import java.util.Optional;

public interface AgentPlanCheckpointStore {
    Optional<AgentPlanCheckpoint> load(int characterId) throws IOException;

    void save(AgentPlanCheckpoint checkpoint) throws IOException;

    void delete(int characterId) throws IOException;
}
