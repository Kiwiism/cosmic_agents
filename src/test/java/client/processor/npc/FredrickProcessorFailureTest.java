package client.processor.npc;

import client.Character;
import client.Client;
import org.junit.jupiter.api.Test;
import service.NoteService;
import tools.DatabaseConnection;

import java.sql.SQLException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FredrickProcessorFailureTest {
    @Test
    void shouldNotWithdrawMesosOrDeleteItemsWhenStorageLoadFails() throws SQLException {
        Client client = mock(Client.class);
        Character chr = mock(Character.class);
        when(client.tryacquireClient()).thenReturn(true);
        when(client.getPlayer()).thenReturn(chr);
        when(chr.getId()).thenReturn(77);
        when(chr.getAccountID()).thenReturn(88);
        when(chr.getName()).thenReturn("FredrickTest");
        FredrickProcessor processor = new FredrickProcessor(mock(NoteService.class));

        try (var database = mockStatic(DatabaseConnection.class)) {
            database.when(DatabaseConnection::getConnection)
                    .thenThrow(new SQLException("merchant storage unavailable"));

            processor.fredrickRetrieveItems(client);

            verify(chr, never()).withdrawMerchantMesos();
            verify(chr).dropMessage(1, FredrickProcessor.LOAD_ERROR_MESSAGE);
            database.verify(DatabaseConnection::getConnection);
        }
        verify(client).releaseClient();
    }
}
