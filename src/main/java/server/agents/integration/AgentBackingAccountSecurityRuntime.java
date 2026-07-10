package server.agents.integration;

import server.agents.auth.AgentBackingAccountSecurityService;
import server.agents.integration.cosmic.CosmicAgentBackingAccountSecurity;

import java.sql.SQLException;

public final class AgentBackingAccountSecurityRuntime {
    private AgentBackingAccountSecurityRuntime() {
    }

    public static boolean lockInteractiveLogin(int accountId) throws SQLException {
        return AgentBackingAccountSecurityService.lockInteractiveLogin(
                accountId,
                CosmicAgentBackingAccountSecurity::lockInteractiveLogin);
    }
}
