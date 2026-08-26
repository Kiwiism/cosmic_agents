package server.agents.field;

import client.Character;
import client.SkinColor;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.agents.integration.InventoryGateway;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentHpqTestFixtureServiceTest {
    @Test
    void appearanceUsesAvailableGenderCompatibleStylesAndOnlyRequestedSkinTones() {
        Set<Integer> genders = new HashSet<>();
        Set<SkinColor> skins = new HashSet<>();

        for (long seed = 0; seed < 500; seed++) {
            AgentHpqAppearanceCatalog.Appearance appearance =
                    AgentHpqAppearanceCatalog.select(seed);
            assertEquals(appearance, AgentHpqAppearanceCatalog.select(seed));
            assertTrue(appearance.gender() == 0 || appearance.gender() == 1);
            assertTrue(AgentHpqAppearanceCatalog.faces(appearance.gender())
                    .contains(appearance.faceId()));
            assertTrue(AgentHpqAppearanceCatalog.hair(appearance.gender())
                    .contains(appearance.hairId()));
            assertTrue(AgentHpqAppearanceCatalog.SKIN_COLORS.contains(appearance.skinColor()));
            genders.add(appearance.gender());
            skins.add(appearance.skinColor());
        }

        assertEquals(Set.of(0, 1), genders);
        assertEquals(Set.of(
                SkinColor.LIGHT, SkinColor.TANNED, SkinColor.DARK, SkinColor.PALE), skins);
        assertTrue(AgentHpqAppearanceCatalog.hair(0).stream().anyMatch(id -> id / 1_000 == 33));
        assertTrue(AgentHpqAppearanceCatalog.hair(1).stream().anyMatch(id -> id / 1_000 == 34));
    }

    @Test
    void appliesTheSelectedGenderSkinHairAndFaceTogether() {
        Character agent = mock(Character.class);
        AgentHpqAppearanceCatalog.Appearance appearance =
                AgentHpqTestFixtureService.applyAppearance(agent, 91_337L);

        verify(agent).setGender(appearance.gender());
        verify(agent).setSkinColor(appearance.skinColor());
        verify(agent).setHair(appearance.hairId());
        verify(agent).setFace(appearance.faceId());
    }

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
        Inventory equip = mock(Inventory.class);
        Inventory equipped = mock(Inventory.class);
        InventoryGateway inventory = mock(InventoryGateway.class);
        Equip worn = Equip.restored(AgentHpqTestFixtureService.RICE_CAKE_HAT, (short) -1);

        when(agent.getInventory(InventoryType.EQUIP)).thenReturn(equip);
        when(agent.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(equipped.getItem((short) -1)).thenReturn(worn);
        when(equip.list()).thenReturn(List.of());

        AgentHpqTestFixtureService.equipRiceCakeHat(agent, inventory);

        verify(inventory, never()).addItem(agent, AgentHpqTestFixtureService.RICE_CAKE_HAT,
                (short) 1);
    }

    @Test
    void reusesAnUnequippedRiceCakeHatAndRemovesTheCompetingFixtureCap() {
        Character agent = mock(Character.class);
        Inventory equip = mock(Inventory.class);
        Inventory equipped = mock(Inventory.class);
        InventoryGateway inventory = mock(InventoryGateway.class);
        Equip template = Equip.restored(AgentHpqTestFixtureService.RICE_CAKE_HAT, (short) 0);
        Equip riceHat = Equip.restored(AgentHpqTestFixtureService.RICE_CAKE_HAT, (short) 4);
        Equip ordinaryCap = Equip.restored(1_002_014, (short) 7);
        Equip worn = Equip.restored(AgentHpqTestFixtureService.RICE_CAKE_HAT, (short) -1);

        when(agent.getInventory(InventoryType.EQUIP)).thenReturn(equip);
        when(agent.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(equipped.getItem((short) -1)).thenReturn(ordinaryCap, worn);
        when(equip.list()).thenReturn(List.of(riceHat), List.of(ordinaryCap));
        when(inventory.getEquipById(AgentHpqTestFixtureService.RICE_CAKE_HAT)).thenReturn(template);
        when(inventory.canWearEquipment(agent, template, (short) -1)).thenReturn(true);
        when(inventory.getEquipmentSlot(ordinaryCap.getItemId())).thenReturn("Cp");

        AgentHpqTestFixtureService.equipRiceCakeHat(agent, inventory);

        verify(inventory, never()).addItem(agent, AgentHpqTestFixtureService.RICE_CAKE_HAT,
                (short) 1);
        verify(inventory).moveItem(agent, InventoryType.EQUIP, (short) 4, (short) -1, (short) 1);
        verify(inventory).removeFromSlot(
                agent, InventoryType.EQUIP, (short) 7, (short) 1, false);
    }
}
