package server.agents.catalog;

import java.util.Optional;

public interface AgentMapStrategyRepository {
    Optional<AgentMapStrategy> find(int mapId);
}
