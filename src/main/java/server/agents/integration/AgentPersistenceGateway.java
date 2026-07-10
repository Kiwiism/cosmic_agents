package server.agents.integration;

import server.agents.registry.AgentResolvedCharacter;

import java.sql.SQLException;

public interface AgentPersistenceGateway {
    AgentResolvedCharacter findCharacterByName(String name) throws SQLException;

    AgentResolvedCharacter findCharacterById(int characterId) throws SQLException;

    Integer getRegisteredOwnerId(int agentCharacterId) throws SQLException;

    void registerOwner(int agentCharacterId, int ownerCharacterId) throws SQLException;

    int countRegisteredAgents(int ownerCharacterId) throws SQLException;

    AgentAccountResolution resolveOrCreateAgentAccount(String name) throws SQLException;
}
