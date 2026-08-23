package client.command.commands.gm6;

import client.Character;
import client.Client;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.auth.AgentAuthorityService;
import server.agents.auth.AgentControlService;
import server.agents.integration.AgentIdentityGateway;
import server.agents.integration.AgentIdentityGatewayRuntime;
import server.agents.integration.AgentIdentityOrigin;
import server.agents.registry.AgentResolvedCharacter;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdoptTestAgentCommandTest {
    @Test
    void adoptsConfirmedOfflineCharacterFromOperatorAccount() throws Exception {
        Client client = mock(Client.class);
        Character actor = mock(Character.class);
        AgentControlService control = mock(AgentControlService.class);
        AgentIdentityGateway identities = mock(AgentIdentityGateway.class);
        AgentResolvedCharacter target = new AgentResolvedCharacter(27, "KiwiAgent", 1, null);
        when(client.getPlayer()).thenReturn(actor);
        when(actor.getId()).thenReturn(1);
        when(actor.getAccountID()).thenReturn(1);
        when(control.resolveCharacterByName("KiwiAgent")).thenReturn(target);
        when(identities.find(27)).thenReturn(Optional.empty());

        try (MockedStatic<AgentAuthorityService> authority = mockStatic(AgentAuthorityService.class);
             MockedStatic<AgentControlService> controls = mockStatic(AgentControlService.class);
             MockedStatic<AgentIdentityGatewayRuntime> identityRuntime =
                     mockStatic(AgentIdentityGatewayRuntime.class)) {
            authority.when(() -> AgentAuthorityService.mayOperate(actor)).thenReturn(true);
            controls.when(AgentControlService::getInstance).thenReturn(control);
            identityRuntime.when(AgentIdentityGatewayRuntime::identities).thenReturn(identities);

            new AdoptTestAgentCommand().execute(client, new String[]{"KiwiAgent", "confirm"});

            verify(identities).register(27, AgentIdentityOrigin.LEGACY_TEST_FIXTURE, true);
        }
    }

    @Test
    void refusesCharacterFromAnotherAccount() throws Exception {
        Client client = mock(Client.class);
        Character actor = mock(Character.class);
        AgentControlService control = mock(AgentControlService.class);
        AgentIdentityGateway identities = mock(AgentIdentityGateway.class);
        AgentResolvedCharacter target = new AgentResolvedCharacter(27, "OtherAgent", 2, null);
        when(client.getPlayer()).thenReturn(actor);
        when(actor.getId()).thenReturn(1);
        when(actor.getAccountID()).thenReturn(1);
        when(control.resolveCharacterByName("OtherAgent")).thenReturn(target);

        try (MockedStatic<AgentAuthorityService> authority = mockStatic(AgentAuthorityService.class);
             MockedStatic<AgentControlService> controls = mockStatic(AgentControlService.class);
             MockedStatic<AgentIdentityGatewayRuntime> identityRuntime =
                     mockStatic(AgentIdentityGatewayRuntime.class)) {
            authority.when(() -> AgentAuthorityService.mayOperate(actor)).thenReturn(true);
            controls.when(AgentControlService::getInstance).thenReturn(control);
            identityRuntime.when(AgentIdentityGatewayRuntime::identities).thenReturn(identities);

            new AdoptTestAgentCommand().execute(client, new String[]{"OtherAgent", "confirm"});

            verify(identities, never()).register(
                    27, AgentIdentityOrigin.LEGACY_TEST_FIXTURE, true);
        }
    }
}
