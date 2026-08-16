package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CosmicInventorySnapshotReaderTest {
    @Test
    void quantityChangesRevisionButNotPhysicalItemFingerprint() {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(42);
        for (InventoryType type : List.of(InventoryType.EQUIPPED, InventoryType.EQUIP,
                InventoryType.USE, InventoryType.SETUP, InventoryType.ETC, InventoryType.CASH)) {
            Inventory inventory = mock(Inventory.class);
            when(inventory.list()).thenReturn(List.of());
            when(character.getInventory(type)).thenReturn(inventory);
        }
        Item item = new Item(4000000, (short) 3, (short) 10);
        when(character.getInventory(InventoryType.ETC).list()).thenReturn(List.of(item));
        CosmicInventorySnapshotReader reader = new CosmicInventorySnapshotReader();

        var before = reader.read(character);
        item.setQuantity((short) 9);
        var after = reader.read(character);

        assertNotEquals(before.revision(), after.revision());
        assertEquals(before.items().getFirst().ref().fingerprint(),
                after.items().getFirst().ref().fingerprint());
        assertEquals(9, after.items().getFirst().quantity());
    }
}
