package server.agents.capabilities.equipment;

import client.Character;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentEquipmentPlanExecutorTest {
    @Test
    void infeasibleUnequipSkipsCashEquipsBeforeWearabilityCheck() {
        Character agent = mock(Character.class);
        Inventory equipped = mock(Inventory.class);
        Equip cashEquip = equip(1001, (short) -1);
        AgentEquipmentPlanExecutor.InfeasibleEquipHooks hooks =
                mock(AgentEquipmentPlanExecutor.InfeasibleEquipHooks.class);
        when(agent.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(equipped.list()).thenReturn(List.of(cashEquip));
        when(hooks.isCashItem(1001)).thenReturn(true);

        AgentEquipmentPlanExecutor.unequipInfeasibleEquipped(agent, hooks);

        verify(hooks, never()).canWearEquipment(agent, cashEquip, (short) -1);
    }

    @Test
    void infeasibleUnequipLeavesWearableEquipAlone() {
        Character agent = mock(Character.class);
        Inventory equipped = mock(Inventory.class);
        Equip equip = equip(1002, (short) -2);
        AgentEquipmentPlanExecutor.InfeasibleEquipHooks hooks =
                mock(AgentEquipmentPlanExecutor.InfeasibleEquipHooks.class);
        when(agent.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(equipped.list()).thenReturn(List.of(equip));
        when(hooks.canWearEquipment(agent, equip, (short) -2)).thenReturn(true);

        AgentEquipmentPlanExecutor.unequipInfeasibleEquipped(agent, hooks);

        verify(hooks).canWearEquipment(agent, equip, (short) -2);
    }

    private static Equip equip(int itemId, short position) {
        Equip equip = mock(Equip.class);
        when(equip.getItemId()).thenReturn(itemId);
        when(equip.getPosition()).thenReturn(position);
        return equip;
    }
}
