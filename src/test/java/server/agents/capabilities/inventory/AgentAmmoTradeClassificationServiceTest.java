package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentAmmoTradeClassificationServiceTest {
    @Test
    void classifiesAmmoGroupsThroughSuppliedRuntimeHooks() {
        Character agent = mock(Character.class);
        Inventory use = mock(Inventory.class);
        Item ownStrongArrow = item(2060002, 10);
        Item crossbowArrow = item(2061000, 10);
        Item ownWeakArrow = item(2060000, 10);
        Item questBullet = item(2330000, 10);
        AtomicBoolean weaponRead = new AtomicBoolean();
        AtomicBoolean tradeableRead = new AtomicBoolean();
        when(agent.getInventory(InventoryType.USE)).thenReturn(use);
        when(use.getSlotLimit()).thenReturn((byte) 4);
        when(use.getItem((short) 1)).thenReturn(ownStrongArrow);
        when(use.getItem((short) 2)).thenReturn(crossbowArrow);
        when(use.getItem((short) 3)).thenReturn(ownWeakArrow);
        when(use.getItem((short) 4)).thenReturn(questBullet);

        AmmoTradeGroups groups = AgentAmmoTradeClassificationService.classifyAmmoTradeGroups(
                agent,
                AgentAmmoTradeClassificationService.AmmoTradeCallbacks.of(
                        () -> {
                            weaponRead.set(true);
                            return WeaponType.BOW;
                        },
                        itemId -> itemId == 2060002 ? 2 : 1,
                        itemId -> itemId == 2330000,
                        () -> {
                            tradeableRead.set(true);
                            return false;
                        }));

        assertEquals(List.of(crossbowArrow), groups.nonOwn());
        assertEquals(List.of(ownWeakArrow, ownStrongArrow), groups.own());
        assertTrue(weaponRead.get());
        assertTrue(tradeableRead.get());
    }

    @Test
    void nextAmmoGroupDelegatesLegacySelection() {
        Item nonOwn = item(2061000, 10);
        Item own = item(2060000, 10);
        AmmoTradeGroups groups = new AmmoTradeGroups(List.of(nonOwn), List.of(own));

        assertEquals("ammo:own", AgentAmmoTradeClassificationService.nextAmmoGroup("ammo:non_own", groups));
        assertNull(AgentAmmoTradeClassificationService.nextAmmoGroup("ammo:own", groups));
    }

    private static Item item(int itemId, int quantity) {
        Item item = mock(Item.class);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getQuantity()).thenReturn((short) quantity);
        return item;
    }
}
