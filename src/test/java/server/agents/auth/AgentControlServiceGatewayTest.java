package server.agents.auth;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentIdentityGateway;
import server.agents.integration.AgentIdentityGatewayRuntime;
import server.agents.integration.AgentPersistenceGateway;
import server.agents.integration.AgentPersistenceGatewayRuntime;
import server.agents.integration.CharacterGateway;
import server.agents.registry.AgentResolvedCharacter;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentControlServiceGatewayTest {
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

            assertSame(resolved, AgentControlService.getInstance().resolveCharacterByName("Alpha"));
        }
    }

    @Test
    void controlRequiresDurableActiveAgentIdentity() throws Exception {
        Character actor = mock(Character.class);
        AgentResolvedCharacter target = new AgentResolvedCharacter(100, "Alpha", 200, null);
        AgentIdentityGateway identities = mock(AgentIdentityGateway.class);
        when(actor.getId()).thenReturn(1);
        when(identities.isActiveAgent(100)).thenReturn(false);

        try (MockedStatic<AgentAuthorityService> authority = mockStatic(AgentAuthorityService.class);
             MockedStatic<AgentIdentityGatewayRuntime> identityRuntime =
                     mockStatic(AgentIdentityGatewayRuntime.class)) {
            authority.when(() -> AgentAuthorityService.mayOperate(actor)).thenReturn(true);
            identityRuntime.when(AgentIdentityGatewayRuntime::identities).thenReturn(identities);

            assertFalse(AgentControlService.getInstance().ensureCanControl(actor, target).allowed());

            when(identities.isActiveAgent(100)).thenReturn(true);
            assertTrue(AgentControlService.getInstance().ensureCanControl(actor, target).allowed());
        }
    }
}
