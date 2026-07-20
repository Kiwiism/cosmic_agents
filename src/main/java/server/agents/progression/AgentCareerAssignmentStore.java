package server.agents.progression;

import java.io.IOException;
import java.util.Optional;

public interface AgentCareerAssignmentStore {
    Optional<AgentCareerAssignment> load(int characterId) throws IOException;

    void save(AgentCareerAssignment assignment) throws IOException;
}
