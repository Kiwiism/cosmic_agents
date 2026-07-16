package server.agents.integration.cosmic;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CosmicMapleIslandCohortIdentityTest {
    @Test
    void namedShowcaseGetsStarterSwordAndKeepsBeginnerClothing() {
        Character character = mock(Character.class);
        Inventory current = new Inventory(character, InventoryType.EQUIPPED, (byte) 24);
        addItem(current, 1040002, (byte) -5);
        addItem(current, 1332066, (byte) -11);
        when(character.getInventory(InventoryType.EQUIPPED)).thenReturn(current);

        CosmicMapleIslandCohortIdentity.applyDefaultStarterWeapon(
                character, itemId -> new Item(itemId, (short) 0, (short) 1));

        ArgumentCaptor<Inventory> equipped = ArgumentCaptor.forClass(Inventory.class);
        verify(character).setInventory(org.mockito.ArgumentMatchers.eq(InventoryType.EQUIPPED), equipped.capture());
        assertEquals(1040002, equipped.getValue().getItem((short) -5).getItemId());
        assertEquals(CosmicMapleIslandCohortIdentity.DEFAULT_STARTER_SWORD_ID,
                equipped.getValue().getItem((short) -11).getItemId());
        assertNull(equipped.getValue().findById(1332066));
        verify(character).recalcLocalStats();
    }

    private static void addItem(Inventory inventory, int itemId, byte position) {
        Item item = new Item(itemId, position, (short) 1);
        inventory.addItemFromDB(item);
    }
}
