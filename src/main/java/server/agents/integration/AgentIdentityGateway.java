package server.agents.integration;

import java.sql.SQLException;
import java.util.Optional;

@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.ASYNC_EXTERNAL,
        rationale = "Durable Agent identity is stored in Cosmic and must not be queried from scheduler shards.")
public interface AgentIdentityGateway {
    Optional<AgentIdentityRecord> find(int characterId) throws SQLException;

    void register(int characterId,
                  AgentIdentityOrigin origin,
                  boolean interactiveAllowed) throws SQLException;

    default boolean isActiveAgent(int characterId) throws SQLException {
        return find(characterId).map(AgentIdentityRecord::isActive).orElse(false);
    }
}
