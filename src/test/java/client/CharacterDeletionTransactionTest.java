package client;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CharacterDeletionTransactionTest {
    @Test
    void shouldCommitSuccessfulDeletionWork() throws SQLException {
        Connection connection = mock(Connection.class);

        String result = Character.runDeletionTransaction(connection, () -> "deleted");

        assertEquals("deleted", result);
        verify(connection).setAutoCommit(false);
        verify(connection).commit();
        verify(connection, never()).rollback();
    }

    @Test
    void shouldRollbackFailedDeletionWork() throws SQLException {
        Connection connection = mock(Connection.class);

        assertThrows(SQLException.class, () -> Character.runDeletionTransaction(connection, () -> {
            throw new SQLException("failed stage");
        }));

        verify(connection).rollback();
        verify(connection, never()).commit();
    }
}
