package server.agents.capabilities.equipment;

import client.Character;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.agents.integration.InventoryGateway;

import java.util.Map;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentEquipmentPlanExecutorTest {
    @Test
    void applyEquipPlanMovesSelectedBagItemThroughInventoryGateway() {
        Character agent = mock(Character.class);
        Equip targetWeapon = equip(1302000, (short) 2);
        InventoryGateway inventory = mock(InventoryGateway.class);

        AgentEquipmentPlanExecutor.applyEquipPlan(
                agent,
                Map.of((short) -11, equip(1302001, (short) -11)),
                Map.of(),
                targetWeapon,
                List.of(),
                inventory);

        verify(inventory).moveItem(agent, InventoryType.EQUIP, (short) 2, (short) -11, (short) 1);
    }

    @Test
    void applyEquipPlanSkipsAlreadyEquippedOrMissingTargets() {
        Character agent = mock(Character.class);
        Equip current = equip(1302000, (short) -11);
        InventoryGateway inventory = mock(InventoryGateway.class);

        AgentEquipmentPlanExecutor.applyEquipPlan(
                agent,
                Map.of((short) -11, current),
                Map.of(),
                current,
                List.of(),
                inventory);

        verify(inventory, never()).moveItem(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyShort(),
                org.mockito.ArgumentMatchers.anyShort(),
                org.mockito.ArgumentMatchers.anyShort());
    }

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
