package server.agents.runtime.commerce;

import java.util.Optional;

/** Durable state port for independently owned Commerce visits. */
public interface AgentCommerceSessionStore {
    void save(AgentCommerceSessionCheckpoint checkpoint);

    Optional<AgentCommerceSessionCheckpoint> load(String agentId);

    void delete(String agentId);
}
