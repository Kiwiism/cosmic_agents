package server.agents.progression;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPepeEquipmentCatalogTest {
    @Test
    void matchesTheExactEquippedWeaponCategoryInsteadOfAnyClassWeapon() {
        Character agent = mock(Character.class);
        when(agent.getJob()).thenReturn(Job.FIGHTER);
        Inventory equipped = new Inventory(agent, InventoryType.EQUIPPED, (byte) 20);
        Inventory equip = new Inventory(agent, InventoryType.EQUIP, (byte) 20);
        Equip currentAxe = Equip.restored(1312005, (short) -11);
        equipped.addItemFromDB(currentAxe);
        Equip pepeAxe = Equip.restored(1312045, (short) 1);
        pepeAxe.setUpgradeSlots((byte) 7);
        equip.addItemFromDB(pepeAxe);
        when(agent.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(agent.getInventory(InventoryType.EQUIP)).thenReturn(equip);

        AgentPepeEquipmentSnapshot facts = AgentPepeEquipmentCatalog.capture(agent);

        assertEquals(1312045, facts.desiredWeaponItemId());
        assertEquals(2043116, facts.scrollItemId());
        assertEquals(1, facts.rewardSelectionIndex());
        assertEquals(7, facts.remainingUpgradeSlots());
        assertTrue(facts.owned());
        assertTrue(facts.scrollable());
    }

    @Test
    void usesTheSecondJobBranchWhenNoWeaponIsEquipped() {
        Character agent = mock(Character.class);
        when(agent.getJob()).thenReturn(Job.ASSASSIN);
        when(agent.getInventory(InventoryType.EQUIPPED))
                .thenReturn(new Inventory(agent, InventoryType.EQUIPPED, (byte) 20));
        when(agent.getInventory(InventoryType.EQUIP))
                .thenReturn(new Inventory(agent, InventoryType.EQUIP, (byte) 20));

        AgentPepeEquipmentSnapshot facts = AgentPepeEquipmentCatalog.capture(agent);

        assertEquals(1472089, facts.desiredWeaponItemId());
        assertEquals(2044711, facts.scrollItemId());
        assertEquals(1, facts.rewardSelectionIndex());
    }

    @Test
    void rewardSelectionsUseTheJobFilteredQuest2337Indexes() {
        assertSelection(Job.SPEARMAN, 1432057, 2044316, 6);
        assertSelection(Job.CLERIC, 1372053, 2043711, 0);
        assertSelection(Job.CROSSBOWMAN, 1462066, 2044611, 1);
        assertSelection(Job.BANDIT, 1332088, 2043311, 0);
        assertSelection(Job.GUNSLINGER, 1492038, 2044909, 1);
    }

    private static void assertSelection(Job job, int weapon, int scroll, int selection) {
        Character agent = mock(Character.class);
        when(agent.getJob()).thenReturn(job);
        when(agent.getInventory(InventoryType.EQUIPPED))
                .thenReturn(new Inventory(agent, InventoryType.EQUIPPED, (byte) 20));
        when(agent.getInventory(InventoryType.EQUIP))
                .thenReturn(new Inventory(agent, InventoryType.EQUIP, (byte) 20));
        AgentPepeEquipmentSnapshot facts = AgentPepeEquipmentCatalog.capture(agent);
        assertEquals(weapon, facts.desiredWeaponItemId());
        assertEquals(scroll, facts.scrollItemId());
        assertEquals(selection, facts.rewardSelectionIndex());
    }
}
