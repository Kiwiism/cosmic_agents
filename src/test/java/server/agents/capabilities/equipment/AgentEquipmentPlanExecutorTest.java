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
import static org.mockito.ArgumentMatchers.anyShort;

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

        verify(hooks, never()).meetsEquipRequirements(agent, cashEquip);
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
        when(hooks.meetsEquipRequirements(agent, equip)).thenReturn(true);

        AgentEquipmentPlanExecutor.unequipInfeasibleEquipped(agent, hooks);

        verify(hooks).meetsEquipRequirements(agent, equip);
    }

    @Test
    void relocateEquippedStraysMovesOnlyPositivePositionEquipsToBag() {
        Character agent = mock(Character.class);
        when(agent.getName()).thenReturn("TestAgent");
        Inventory equipInventory = mock(Inventory.class);
        Inventory equippedInventory = mock(Inventory.class);
        Equip stray = equip(1003, (short) 1);
        Equip worn = equip(1004, (short) -5);
        when(equippedInventory.list()).thenReturn(List.of(stray, worn));
        when(equipInventory.getNextFreeSlot()).thenReturn((short) 7);

        AgentEquipmentPlanExecutor.relocateEquippedStrays(agent, equipInventory, equippedInventory);

        verify(equippedInventory).removeSlot((short) 1);
        verify(stray).setPosition((short) 7);
        verify(equipInventory).addItemFromDB(stray);
        verify(equippedInventory, never()).removeSlot((short) -5);
        verify(worn, never()).setPosition(anyShort());
    }

    private static Equip equip(int itemId, short position) {
        Equip equip = mock(Equip.class);
        when(equip.getItemId()).thenReturn(itemId);
        when(equip.getPosition()).thenReturn(position);
        return equip;
    }
}
