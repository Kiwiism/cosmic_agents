package server.agents.capabilities.inventory;

import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentInventorySellTrashServiceTest {
    @Test
    void shouldKeepOnlyUnprotectedEquipsForSellTrash() {
        Equip sellable = equip(1000001);
        Equip scrolled = equip(1000002);
        Equip attackAccessory = equip(1000003);
        Item nonEquip = item(2000000);

        when(scrolled.getLevel()).thenReturn((byte) 1);
        when(attackAccessory.getWatk()).thenReturn((short) 1);

        List<Item> trash = AgentInventorySellTrashService.collectSellTrashEquips(
                List.of(scrolled, sellable, nonEquip, attackAccessory),
                null);

        assertEquals(List.of(sellable), trash);
    }

    private static Equip equip(int itemId) {
        Equip equip = mock(Equip.class);
        when(equip.getItemId()).thenReturn(itemId);
        when(equip.getInventoryType()).thenReturn(InventoryType.EQUIP);
        return equip;
    }

    private static Item item(int itemId) {
        Item item = mock(Item.class);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getInventoryType()).thenReturn(InventoryType.USE);
        return item;
    }
}
