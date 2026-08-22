package server.agents.integration;

import server.agents.registry.AgentResolvedCharacter;

import java.sql.SQLException;
import java.util.List;

@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.ASYNC_EXTERNAL,
        rationale = "Persistence operations must execute off scheduler shards and return stamped completions.")
public interface AgentPersistenceGateway {
    AgentResolvedCharacter findCharacterByName(String name) throws SQLException;

    AgentResolvedCharacter findCharacterById(int characterId) throws SQLException;

    /** Agent-only backing characters available to operator and policy Directors. */
    default List<AgentPersistedCharacterSummary> listAgentCharacters() throws SQLException {
        return List.of();
    }

    AgentAccountResolution resolveOrCreateAgentAccount(String name) throws SQLException;
}
