package server.agents.auth;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentPersistenceGateway;
import server.agents.integration.AgentPersistenceGatewayRuntime;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentAccountAccessPolicyTest {
    @Test
    void rejectsAgentBackingAccount() throws Exception {
        AgentPersistenceGateway persistence = mock(AgentPersistenceGateway.class);
        when(persistence.isAgentAccount(42)).thenReturn(true);

        try (MockedStatic<AgentPersistenceGatewayRuntime> runtime = mockStatic(AgentPersistenceGatewayRuntime.class)) {
            runtime.when(AgentPersistenceGatewayRuntime::persistence).thenReturn(persistence);
            assertFalse(AgentAccountAccessPolicy.allowsInteractiveLogin(42));
        }
    }

    @Test
    void permitsOrdinaryAccount() throws Exception {
        AgentPersistenceGateway persistence = mock(AgentPersistenceGateway.class);
        when(persistence.isAgentAccount(42)).thenReturn(false);

        try (MockedStatic<AgentPersistenceGatewayRuntime> runtime = mockStatic(AgentPersistenceGatewayRuntime.class)) {
            runtime.when(AgentPersistenceGatewayRuntime::persistence).thenReturn(persistence);
            assertTrue(AgentAccountAccessPolicy.allowsInteractiveLogin(42));
        }
    }

    @Test
    void failsClosedWhenPolicyLookupFails() throws Exception {
        AgentPersistenceGateway persistence = mock(AgentPersistenceGateway.class);
        when(persistence.isAgentAccount(42)).thenThrow(new SQLException("unavailable"));

        try (MockedStatic<AgentPersistenceGatewayRuntime> runtime = mockStatic(AgentPersistenceGatewayRuntime.class)) {
            runtime.when(AgentPersistenceGatewayRuntime::persistence).thenReturn(persistence);
            assertFalse(AgentAccountAccessPolicy.allowsInteractiveLogin(42));
        }
    }
}
