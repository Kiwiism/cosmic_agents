package server.agents.auth;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBackingAccountSecurityServiceTest {
    @Test
    void locksValidBackingAccountThroughAdapter() throws Exception {
        AtomicInteger lockedAccount = new AtomicInteger();

        boolean locked = AgentBackingAccountSecurityService.lockInteractiveLogin(42, accountId -> {
            lockedAccount.set(accountId);
            return true;
        });

        assertTrue(locked);
        assertTrue(lockedAccount.get() == 42);
    }

    @Test
    void rejectsInvalidAccountWithoutCallingAdapter() throws Exception {
        assertFalse(AgentBackingAccountSecurityService.lockInteractiveLogin(0, accountId -> {
            throw new AssertionError("adapter should not be called");
        }));
    }

    @Test
    void propagatesPersistenceFailureForFailClosedCaller() {
        assertThrows(SQLException.class, () ->
                AgentBackingAccountSecurityService.lockInteractiveLogin(42, accountId -> {
                    throw new SQLException("unavailable");
                }));
    }
}
