package scripting.npc;

import client.Character;
import client.Client;
import client.processor.npc.FredrickProcessor;
import org.junit.jupiter.api.Test;
import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FredrickConversationTest {
    @Test
    void shouldReuseSuccessfulStorageLoadWhenOpeningFredrick() throws SQLException {
        Client client = mock(Client.class);
        Character chr = character(client);
        when(chr.getMerchantMeso()).thenReturn(1);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        NPCConversationManager conversation = new NPCConversationManager(client, 9030000, "9030000");

        try (var database = mockStatic(DatabaseConnection.class)) {
            database.when(DatabaseConnection::getConnection).thenReturn(connection);

            assertTrue(conversation.hasMerchantItems());
            conversation.showFredrick();

            database.verify(DatabaseConnection::getConnection, times(1));
            verify(client).sendPacket(any());
            assertFalse(conversation.hasFredrickLoadFailed());
        }
    }

    @Test
    void shouldExposeLoadFailureInsteadOfReportingEmptyStorage() throws SQLException {
        Client client = mock(Client.class);
        Character chr = character(client);
        NPCConversationManager conversation = new NPCConversationManager(client, 9030000, "9030000");

        try (var database = mockStatic(DatabaseConnection.class)) {
            database.when(DatabaseConnection::getConnection)
                    .thenThrow(new SQLException("merchant storage unavailable"));

            assertFalse(conversation.hasMerchantItems());
            assertTrue(conversation.hasFredrickLoadFailed());
            verify(client, never()).sendPacket(any());
        }
    }

    @Test
    void shouldNotifyPlayerWhenDirectDisplayLoadFails() throws SQLException {
        Client client = mock(Client.class);
        Character chr = character(client);
        NPCConversationManager conversation = new NPCConversationManager(client, 9030000, "9030000");

        try (var database = mockStatic(DatabaseConnection.class)) {
            database.when(DatabaseConnection::getConnection)
                    .thenThrow(new SQLException("merchant storage unavailable"));

            conversation.showFredrick();

            assertTrue(conversation.hasFredrickLoadFailed());
            verify(chr).dropMessage(1, FredrickProcessor.LOAD_ERROR_MESSAGE);
            verify(client, never()).sendPacket(any());
        }
    }

    private static Character character(Client client) {
        Character chr = mock(Character.class);
        when(client.getPlayer()).thenReturn(chr);
        when(chr.getId()).thenReturn(77);
        when(chr.getAccountID()).thenReturn(88);
        when(chr.getName()).thenReturn("FredrickTest");
        return chr;
    }
}
