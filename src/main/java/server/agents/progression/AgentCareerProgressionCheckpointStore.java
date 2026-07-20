package server.agents.progression;

import java.io.IOException;
import java.util.Optional;

public interface AgentCareerProgressionCheckpointStore {
    Optional<AgentCareerProgressionCheckpoint> load(int characterId) throws IOException;

    void save(AgentCareerProgressionCheckpoint checkpoint) throws IOException;

    void delete(int characterId) throws IOException;
}
