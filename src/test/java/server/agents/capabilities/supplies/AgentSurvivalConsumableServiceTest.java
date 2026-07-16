package server.agents.capabilities.supplies;

import client.Character;
import client.Disease;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.id.ItemId;
import net.server.channel.handlers.UseItemHandler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentSurvivalConsumableServiceTest {
    @Test
    void prefersSpecificCureForOneDisease() {
        Character partner = mock(Character.class);
        when(partner.hasDisease(Disease.DARKNESS)).thenReturn(true);

        assertEquals(
                List.of(ItemId.EYEDROP, ItemId.ALL_CURE_POTION),
                AgentSurvivalConsumableService.cureCandidates(partner));
    }

    @Test
    void consumesAllCureFirstForMultipleDiseaseGroups() {
        Character partner = mock(Character.class);
        Inventory use = mock(Inventory.class);
        Item allCure = mock(Item.class);
        when(partner.isAlive()).thenReturn(true);
        when(partner.hasDisease(Disease.DARKNESS)).thenReturn(true);
        when(partner.hasDisease(Disease.SEAL)).thenReturn(true);
        when(partner.getInventory(InventoryType.USE)).thenReturn(use);
        when(use.findById(ItemId.ALL_CURE_POTION)).thenReturn(allCure);
        when(allCure.getQuantity()).thenReturn((short) 1);
        when(allCure.getPosition()).thenReturn((short) 4);

        try (MockedStatic<UseItemHandler> items = mockStatic(UseItemHandler.class)) {
            items.when(() -> UseItemHandler.consumeUseItem(
                    partner, (short) 4, ItemId.ALL_CURE_POTION)).thenReturn(true);

            assertTrue(AgentSurvivalConsumableService.tryUseDiseaseCure(partner));
            items.verify(() -> UseItemHandler.consumeUseItem(
                    partner, (short) 4, ItemId.ALL_CURE_POTION));
        }
    }
}
