package server.agents.capabilities.supplies;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.StatEffect;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPotionSharePolicyTest {
    @Test
    void shouldScoreFlatPotionsBeforeRatePotionsLikeLegacyShareSorting() {
        assertEquals(50, AgentPotionSharePolicy.recoveryScore(effect(50, 0, 0, 0), true));
        assertEquals(100, AgentPotionSharePolicy.recoveryScore(effect(0, 100, 0, 0), false));
        assertEquals(1_000_250, AgentPotionSharePolicy.recoveryScore(effect(0, 0, 0.25, 0), true));
        assertEquals(1_000_400, AgentPotionSharePolicy.recoveryScore(effect(0, 0, 0, 0.4), false));
        assertEquals(Integer.MAX_VALUE, AgentPotionSharePolicy.recoveryScore(null, true));
    }

    @Test
    void shouldMatchPotionEffectsToRequestedShareSlot() {
        StatEffect hpFlat = effect(50, 0, 0, 0);
        StatEffect mpFlat = effect(0, 50, 0, 0);
        StatEffect hpRate = effect(0, 0, 0.2, 0);
        StatEffect mpRate = effect(0, 0, 0, 0.2);

        assertTrue(AgentPotionSharePolicy.canShareForSlot(hpFlat, true));
        assertTrue(AgentPotionSharePolicy.canShareForSlot(hpRate, true));
        assertFalse(AgentPotionSharePolicy.canShareForSlot(hpFlat, false));

        assertTrue(AgentPotionSharePolicy.canShareForSlot(mpFlat, false));
        assertTrue(AgentPotionSharePolicy.canShareForSlot(mpRate, false));
        assertFalse(AgentPotionSharePolicy.canShareForSlot(mpFlat, true));
        assertFalse(AgentPotionSharePolicy.canShareForSlot(null, true));
    }

    @Test
    void shouldCollectWorstRecoveryPotionsForRequestedSlot() {
        Character donor = mock(Character.class);
        Inventory use = mock(Inventory.class);
        Item hpSmall = item(2001, 2);
        Item mpOnly = item(2002, 4);
        Item hpLarge = item(2003, 3);
        when(donor.getInventory(InventoryType.USE)).thenReturn(use);
        when(use.getSlotLimit()).thenReturn((byte) 4);
        when(use.getItem((short) 1)).thenReturn(hpLarge);
        when(use.getItem((short) 2)).thenReturn(mpOnly);
        when(use.getItem((short) 3)).thenReturn(hpSmall);

        List<Item> items = AgentPotionSharePolicy.collectShareItems(donor, true, 99,
                itemId -> itemId == 2001 || itemId == 2002 || itemId == 2003,
                itemId -> switch (itemId) {
                    case 2001 -> effect(50, 0, 0, 0);
                    case 2002 -> effect(0, 50, 0, 0);
                    case 2003 -> effect(200, 0, 0, 0);
                    default -> null;
                });

        assertEquals(List.of(hpSmall, hpLarge), items);
    }

    @Test
    void shouldStopCollectingPotionStacksAtShareQuantityBudget() {
        Character donor = mock(Character.class);
        Inventory use = mock(Inventory.class);
        Item first = item(2010, 6);
        Item second = item(2011, 6);
        when(donor.getInventory(InventoryType.USE)).thenReturn(use);
        when(use.getSlotLimit()).thenReturn((byte) 2);
        when(use.getItem((short) 1)).thenReturn(first);
        when(use.getItem((short) 2)).thenReturn(second);

        List<Item> items = AgentPotionSharePolicy.collectShareItems(donor, true, 6,
                itemId -> true,
                itemId -> effect(itemId == 2010 ? 50 : 100, 0, 0, 0));

        assertEquals(List.of(first), items);
    }

    private static StatEffect effect(int hp, int mp, double hpRate, double mpRate) {
        StatEffect effect = mock(StatEffect.class);
        when(effect.getHp()).thenReturn((short) hp);
        when(effect.getMp()).thenReturn((short) mp);
        when(effect.getHpRate()).thenReturn(hpRate);
        when(effect.getMpRate()).thenReturn(mpRate);
        return effect;
    }

    private static Item item(int itemId, int quantity) {
        Item item = mock(Item.class);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getQuantity()).thenReturn((short) quantity);
        return item;
    }
}
