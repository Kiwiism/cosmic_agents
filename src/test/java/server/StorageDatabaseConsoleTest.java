package server;

import client.inventory.Item;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StorageDatabaseConsoleTest {
    @Test
    void upsertsAndReplacesItemsByPosition() throws Exception {
        Storage storage = storage(8);
        storage.upsertFromDatabaseConsole(null, null, item(2000000, 1, 5));
        storage.upsertFromDatabaseConsole((short) 1, 2000000, item(2000001, 3, 2));

        assertEquals(1, storage.getItems().size());
        assertEquals(2000001, storage.getItems().getFirst().getItemId());
        assertEquals(3, storage.getItems().getFirst().getPosition());
        assertEquals(2, storage.getItems().getFirst().getQuantity());
    }

    @Test
    void rejectsOccupiedTargetAndStaleSource() throws Exception {
        Storage storage = storage(8);
        storage.upsertFromDatabaseConsole(null, null, item(2000000, 1, 1));
        storage.upsertFromDatabaseConsole(null, null, item(4000000, 2, 1));

        assertThrows(IllegalStateException.class,
                () -> storage.upsertFromDatabaseConsole((short) 1, 2000000, item(2000001, 2, 1)));
        assertThrows(IllegalStateException.class,
                () -> storage.deleteFromDatabaseConsole((short) 1, 2000001));
    }

    @Test
    void swapsAndDeletesExpectedItems() throws Exception {
        Storage storage = storage(8);
        storage.upsertFromDatabaseConsole(null, null, item(2000000, 1, 1));
        storage.upsertFromDatabaseConsole(null, null, item(4000000, 2, 1));

        storage.swapFromDatabaseConsole((short) 1, 2000000, (short) 2, 4000000);
        assertEquals(2, storage.getItems().stream().filter(item -> item.getItemId() == 2000000)
                .findFirst().orElseThrow().getPosition());

        storage.deleteFromDatabaseConsole((short) 2, 2000000);
        assertEquals(1, storage.getItems().size());
    }

    @Test
    void preventsShrinkingBelowAnOccupiedPosition() throws Exception {
        Storage storage = storage(8);
        storage.upsertFromDatabaseConsole(null, null, item(2000000, 8, 1));

        assertThrows(IllegalStateException.class, () -> storage.updateFromDatabaseConsole(4, 0));
    }

    private Storage storage(int slots) throws Exception {
        Constructor<Storage> constructor = Storage.class.getDeclaredConstructor(int.class, byte.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(1, (byte) slots, 0);
    }

    private Item item(int itemId, int position, int quantity) {
        return new Item(itemId, (short) position, (short) quantity);
    }
}
