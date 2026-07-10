package server.agents.auth;

import java.sql.SQLException;

/** Applies the fail-closed interactive-login lock required for Agent backing accounts. */
public final class AgentBackingAccountSecurityService {
    @FunctionalInterface
    public interface AccountLocker {
        boolean lock(int accountId) throws SQLException;
    }

    private AgentBackingAccountSecurityService() {
    }

    public static boolean lockInteractiveLogin(int accountId, AccountLocker locker) throws SQLException {
        if (accountId <= 0 || locker == null) {
            return false;
        }
        return locker.lock(accountId);
    }
}
