package server.agents.personality;

import java.io.IOException;
import java.util.Optional;

public interface AgentPersonalityAssignmentStore {
    Optional<AgentPersonalityAssignment> load(int characterId) throws IOException;

    void save(AgentPersonalityAssignment assignment) throws IOException;
}
