package server.agents.auth;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentPersistenceGateway;
import server.agents.integration.AgentPersistenceGatewayRuntime;
import server.agents.integration.CharacterGateway;
import server.agents.registry.AgentResolvedCharacter;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentOwnershipServiceGatewayTest {
    @Test
    void resolvesOfflineCharacterThroughPersistenceGateway() throws Exception {
        CharacterGateway characters = mock(CharacterGateway.class);
        AgentPersistenceGateway persistence = mock(AgentPersistenceGateway.class);
        AgentResolvedCharacter resolved = new AgentResolvedCharacter(100, "Alpha", 200, null);
        when(characters.findOnlineCharacterByName("Alpha")).thenReturn(null);
        when(persistence.findCharacterByName("Alpha")).thenReturn(resolved);

        try (MockedStatic<AgentCharacterGatewayRuntime> characterRuntime = mockStatic(AgentCharacterGatewayRuntime.class);
             MockedStatic<AgentPersistenceGatewayRuntime> persistenceRuntime = mockStatic(AgentPersistenceGatewayRuntime.class)) {
            characterRuntime.when(AgentCharacterGatewayRuntime::characters).thenReturn(characters);
            persistenceRuntime.when(AgentPersistenceGatewayRuntime::persistence).thenReturn(persistence);

            assertSame(resolved, AgentOwnershipService.getInstance().resolveCharacterByName("Alpha"));
        }
    }
}
