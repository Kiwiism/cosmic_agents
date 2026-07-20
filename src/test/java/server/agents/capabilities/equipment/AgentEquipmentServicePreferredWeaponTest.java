package server.agents.capabilities.equipment;

import client.Character;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.agents.integration.InventoryGateway;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentEquipmentServicePreferredWeaponTest {
    @Test
    void equipsCatalogSelectedStarterWeaponEvenWhenAnotherWeaponWasOptimized() {
        Character agent = mock(Character.class);
        Inventory equipped = mock(Inventory.class);
        Inventory bag = mock(Inventory.class);
        Equip preferred = mock(Equip.class);
        InventoryGateway inventory = mock(InventoryGateway.class);
        when(agent.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(agent.getInventory(InventoryType.EQUIP)).thenReturn(bag);
        when(equipped.getItem((short) -11)).thenReturn(null, preferred);
        when(bag.list()).thenReturn(List.of(preferred));
        when(preferred.getItemId()).thenReturn(1472061);
        when(preferred.getPosition()).thenReturn((short) 2);
        when(inventory.canWearEquipment(agent, preferred, (short) -11)).thenReturn(true);

        assertTrue(AgentEquipmentService.equipPreferredWeapon(agent, 1472061, inventory));

        verify(inventory).moveItem(agent, InventoryType.EQUIP, (short) 2, (short) -11, (short) 1);
    }
}
