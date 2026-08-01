package client.processor.npc;

import client.Character;
import client.Client;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DueyProcessorAccessTest {
    @Test
    void removalScopesPackageIdToCurrentReceiver() throws Exception {
        Client client = lockedClient(91);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(contains("DELETE FROM dueypackages"))).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);

        try (MockedStatic<DatabaseConnection> database = mockStatic(DatabaseConnection.class)) {
            database.when(DatabaseConnection::getConnection).thenReturn(connection);

            DueyProcessor.dueyRemovePackage(client, 42, true);
        }

        verify(statement).setInt(1, 42);
        verify(statement).setInt(2, 91);
        verify(connection).rollback();
        verify(client).releaseClient();
    }

    @Test
    void claimLookupScopesPackageIdToCurrentReceiver() throws Exception {
        Client client = lockedClient(91);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        when(connection.prepareStatement(contains("SELECT * FROM dueypackages"))).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(false);

        try (MockedStatic<DatabaseConnection> database = mockStatic(DatabaseConnection.class)) {
            database.when(DatabaseConnection::getConnection).thenReturn(connection);

            DueyProcessor.dueyClaimPackage(client, 42);
        }

        verify(statement).setInt(1, 42);
        verify(statement).setInt(2, 91);
        verify(client).releaseClient();
    }

    private static Client lockedClient(int characterId) {
        Client client = mock(Client.class);
        Character character = mock(Character.class);
        when(client.tryacquireClient()).thenReturn(true);
        when(client.getPlayer()).thenReturn(character);
        when(character.getId()).thenReturn(characterId);
        when(character.getName()).thenReturn("Receiver");
        return client;
    }
}
