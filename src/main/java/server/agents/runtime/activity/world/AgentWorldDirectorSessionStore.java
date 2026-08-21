package server.agents.runtime.activity.world;

import java.util.Optional;

public interface AgentWorldDirectorSessionStore {
    void save(AgentWorldDirectorSession session);

    Optional<AgentWorldDirectorSession> load(int agentId);

    void delete(int agentId);
}
