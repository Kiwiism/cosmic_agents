package server.agents.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.integration.AgentPersistenceGatewayRuntime;

import java.sql.SQLException;

/** Prevents a headless Agent backing account from being used as a normal player login. */
public final class AgentAccountAccessPolicy {
    private static final Logger log = LoggerFactory.getLogger(AgentAccountAccessPolicy.class);

    private AgentAccountAccessPolicy() {
    }

    public static boolean allowsInteractiveLogin(int accountId) {
        try {
            return !AgentPersistenceGatewayRuntime.persistence().isAgentAccount(accountId);
        } catch (SQLException e) {
            log.warn("Unable to verify interactive-login policy for account {}", accountId, e);
            return false;
        }
    }
}
