package server.agents.capabilities.equipment;

import client.Character;
import client.inventory.Equip;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentEquipmentRecommendationServiceTest {
    @Test
    void summaryReturnsNullWhenThereAreNoRecommendations() {
        assertNull(AgentEquipmentRecommendationService.formatRecommendationSummary(
                List.of(), 3, itemId -> "item-" + itemId));
    }

    @Test
    void summaryPreservesLegacySlotNamesLimitAndOverflowCount() {
        Equip weapon = equip(1302000);
        Equip glove = equip(1082000);
        Equip unknown = equip(9999999);
        List<AgentEquipRecommendation> recommendations = List.of(
                new AgentEquipRecommendation((short) -11, null, weapon),
                new AgentEquipRecommendation((short) -8, null, glove),
                new AgentEquipRecommendation((short) 42, null, unknown));

        assertEquals("better gear for you: weapon -> item-1302000, glove -> item-1082000 +1 more",
                AgentEquipmentRecommendationService.formatRecommendationSummary(
                        recommendations, 2, itemId -> "item-" + itemId));
    }

    @Test
    void recommendationDefersWhenRecipientInventoryWasReleasedAfterLogout() {
        Character recipient = mock(Character.class);

        assertTrue(AgentEquipmentRecommendationService.findRecommendedEquipsFromItems(
                recipient, List.of(equip(1302000))).isEmpty());
        verify(recipient, never()).getInventory(any());
    }

    @Test
    void recommendationDefersWhenItemHolderInventoryWasReleasedAfterLogout() {
        Character recipient = mock(Character.class);
        Character holder = mock(Character.class);
        when(recipient.hasInventoryState()).thenReturn(true);

        assertTrue(AgentEquipmentRecommendationService.findRecommendedEquips(recipient, holder).isEmpty());
        verify(holder, never()).getInventory(any());
    }

    private static Equip equip(int itemId) {
        Equip equip = mock(Equip.class);
        when(equip.getItemId()).thenReturn(itemId);
        return equip;
    }
}
