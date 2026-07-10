package server.agents.integration.cosmic;

import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Cosmic account-table adapter for preventing interactive use of headless Agent accounts. */
public final class CosmicAgentBackingAccountSecurity {
    static final String AGENT_ONLY_BAN_REASON = "Agent-only backing account";

    private CosmicAgentBackingAccountSecurity() {
    }

    public static boolean lockInteractiveLogin(int accountId) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE accounts SET banned = 1, banreason = ? WHERE id = ?")) {
            statement.setString(1, AGENT_ONLY_BAN_REASON);
            statement.setInt(2, accountId);
            return statement.executeUpdate() == 1;
        }
    }
}
