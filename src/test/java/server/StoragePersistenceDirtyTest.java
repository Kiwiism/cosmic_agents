package server;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

class StoragePersistenceDirtyTest {
    @Test
    void mutationsNotifyTheAttachedCharacter() throws Exception {
        Storage storage = newStorage();
        Character owner = mock(Character.class);
        storage.attachPersistenceOwner(owner);

        Item item = new Item(2000000, (short) 1, (short) 1);
        assertTrue(storage.store(item));

        verify(owner).setUsedStorage();

        reset(owner);
        item.setQuantity((short) 2);
        verify(owner).setUsedStorage();
    }

    @Test
    void databaseFailurePropagatesToTheCharacterTransaction() throws Exception {
        Storage storage = newStorage();
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("write failed"));

        assertThrows(SQLException.class, () -> storage.saveToDB(connection));
    }

    @Test
    void missingStorageRowFailsTheCharacterTransaction() throws Exception {
        Storage storage = newStorage();
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);

        assertThrows(SQLException.class, () -> storage.saveToDB(connection));
    }

    @Test
    void storageSlotsMesoAndItemsRoundTripThroughProductionSnapshot() throws Exception {
        Storage original = newStorage();
        original.updateFromDatabaseConsole(8, 123456);
        original.store(new Item(2000000, (short) 2, (short) 17));

        Storage restored = Storage.fromPersistenceSnapshot(original.persistenceSnapshot());

        assertEquals(8, restored.getSlots());
        assertEquals(123456, restored.getMeso());
        assertEquals(1, restored.getItems().size());
        assertEquals(2000000, restored.getItems().getFirst().getItemId());
        assertEquals(2, restored.getItems().getFirst().getPosition());
        assertEquals(17, restored.getItems().getFirst().getQuantity());
    }

    private static Storage newStorage() throws Exception {
        Constructor<Storage> constructor = Storage.class.getDeclaredConstructor(int.class, byte.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(1, (byte) 4, 0);
    }
}
