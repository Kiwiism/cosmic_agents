package server.agents.runtime.activity.control;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentAccountResolution;
import server.agents.integration.AgentPersistedCharacterSummary;
import server.agents.integration.AgentPersistenceGateway;
import server.agents.registry.AgentResolvedCharacter;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentWorldDirectorAgentDirectoryTest {
    @Test
    void listsAgentOnlyBackingCharactersWithoutLoadingThem() {
        AgentPersistenceGateway persistence = new AgentPersistenceGateway() {
            @Override public AgentResolvedCharacter findCharacterByName(String name) { return null; }
            @Override public AgentResolvedCharacter findCharacterById(int characterId) { return null; }
            @Override public List<AgentPersistedCharacterSummary> listAgentCharacters() {
                return List.of(new AgentPersistedCharacterSummary(
                        99127, "OfflineDirectorAgent", 7, 15, 100, 104000000));
            }
            @Override public AgentAccountResolution resolveOrCreateAgentAccount(String name)
                    throws SQLException { return null; }
        };

        var result = new AgentWorldDirectorAgentDirectory(persistence).list();

        assertEquals(1, result.size());
        assertEquals("OfflineDirectorAgent", result.getFirst().name());
        assertFalse(result.getFirst().online());
        assertFalse(result.getFirst().runtimeActive());
    }
}
