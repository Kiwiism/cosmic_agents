package server.agents.objectives;

import java.io.IOException;
import java.util.Optional;

public interface AgentObjectiveCheckpointStore {
    Optional<AgentObjectiveCheckpoint> load(int characterId) throws IOException;

    void save(AgentObjectiveCheckpoint checkpoint) throws IOException;

    void delete(int characterId) throws IOException;
}
