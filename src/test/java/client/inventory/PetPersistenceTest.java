package client.inventory;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PetPersistenceTest {
    @Test
    void shouldDeletePetAndIgnoreRowsOnProvidedConnection() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement ignoreDelete = mock(PreparedStatement.class);
        PreparedStatement petDelete = mock(PreparedStatement.class);
        when(connection.prepareStatement("DELETE FROM petignores WHERE petid = ?")).thenReturn(ignoreDelete);
        when(connection.prepareStatement("DELETE FROM pets WHERE petid = ?")).thenReturn(petDelete);

        Pet.deleteFromDb(connection, 1234);

        verify(ignoreDelete).setInt(1, 1234);
        verify(ignoreDelete).executeUpdate();
        verify(petDelete).setInt(1, 1234);
        verify(petDelete).executeUpdate();
    }

    @Test
    void shouldRollbackPetDeletionWhenAnyDependentDeleteFails() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement ignoreDelete = mock(PreparedStatement.class);
        PreparedStatement petDelete = mock(PreparedStatement.class);
        when(connection.prepareStatement("DELETE FROM petignores WHERE petid = ?")).thenReturn(ignoreDelete);
        when(connection.prepareStatement("DELETE FROM pets WHERE petid = ?")).thenReturn(petDelete);
        when(petDelete.executeUpdate()).thenThrow(new SQLException("pet delete failed"));

        assertThrows(SQLException.class, () -> Pet.deleteFromDbTransaction(connection, 1234));

        verify(connection).setAutoCommit(false);
        verify(connection).rollback();
        verify(connection, never()).commit();
    }
}
