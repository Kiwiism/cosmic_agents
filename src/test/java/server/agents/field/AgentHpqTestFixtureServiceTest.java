package server.agents.field;

import client.Character;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.agents.integration.InventoryGateway;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentHpqTestFixtureServiceTest {
    @Test
    void equipsTheLevelFifteenHpqRiceCakeHat() {
        Character agent = mock(Character.class);
        Inventory equip = mock(Inventory.class);
        Inventory equipped = mock(Inventory.class);
        InventoryGateway inventory = mock(InventoryGateway.class);
        Equip template = Equip.restored(AgentHpqTestFixtureService.RICE_CAKE_HAT, (short) 0);
        Equip granted = Equip.restored(AgentHpqTestFixtureService.RICE_CAKE_HAT, (short) 3);
        Equip worn = Equip.restored(AgentHpqTestFixtureService.RICE_CAKE_HAT, (short) -1);

        when(agent.getInventory(InventoryType.EQUIP)).thenReturn(equip);
        when(agent.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(equipped.getItem((short) -1)).thenReturn(null, worn);
        when(inventory.getEquipById(AgentHpqTestFixtureService.RICE_CAKE_HAT)).thenReturn(template);
        when(inventory.canWearEquipment(agent, template, (short) -1)).thenReturn(true);
        when(inventory.addItem(agent, AgentHpqTestFixtureService.RICE_CAKE_HAT, (short) 1))
                .thenReturn(true);
        when(equip.list()).thenReturn(List.of(granted));

        AgentHpqTestFixtureService.equipRiceCakeHat(agent, inventory);

        assertEquals(1_002_798, AgentHpqTestFixtureService.RICE_CAKE_HAT);
        verify(inventory).moveItem(agent, InventoryType.EQUIP, (short) 3, (short) -1, (short) 1);
    }

    @Test
    void doesNotDuplicateAnAlreadyEquippedRiceCakeHat() {
        Character agent = mock(Character.class);
        Inventory equipped = mock(Inventory.class);
        InventoryGateway inventory = mock(InventoryGateway.class);
        Equip worn = Equip.restored(AgentHpqTestFixtureService.RICE_CAKE_HAT, (short) -1);

        when(agent.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(equipped.getItem((short) -1)).thenReturn(worn);

        AgentHpqTestFixtureService.equipRiceCakeHat(agent, inventory);

        verify(inventory, never()).addItem(agent, AgentHpqTestFixtureService.RICE_CAKE_HAT,
                (short) 1);
    }
}
