package server.agents.capabilities.shop;

import org.junit.jupiter.api.Test;
import server.ShopItem;
import server.StatEffect;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentShopPotionPolicyTest {
    @Test
    void shouldChooseCheapestPotionInsideRecoveryBand() {
        ShopItem expensive = shopItem(2000, 300);
        ShopItem cheap = shopItem(2001, 100);
        Map<Integer, StatEffect> effects = Map.of(
                2000, effect(300, 0, 0, 0),
                2001, effect(250, 0, 0, 0));

        AgentShopPotionPolicy.PotionShopSlot selected = AgentShopPotionPolicy.selectPotionItem(
                List.of(expensive, cheap), 1000, true, effects::containsKey, effects::get);

        assertEquals(1, selected.slot());
        assertEquals(cheap, selected.shopItem());
    }

    @Test
    void shouldFallbackToStrongestPotionBelowBand() {
        ShopItem weak = shopItem(2000, 100);
        ShopItem stronger = shopItem(2001, 100);
        Map<Integer, StatEffect> effects = Map.of(
                2000, effect(20, 0, 0, 0),
                2001, effect(80, 0, 0, 0));

        AgentShopPotionPolicy.PotionShopSlot selected = AgentShopPotionPolicy.selectPotionItem(
                List.of(weak, stronger), 1000, true, effects::containsKey, effects::get);

        assertEquals(1, selected.slot());
        assertEquals(stronger, selected.shopItem());
    }

    @Test
    void shouldFallbackToWeakestPotionAboveBandWhenNoLowerChoiceExists() {
        ShopItem huge = shopItem(2000, 100);
        ShopItem smaller = shopItem(2001, 100);
        Map<Integer, StatEffect> effects = Map.of(
                2000, effect(900, 0, 0, 0),
                2001, effect(700, 0, 0, 0));

        AgentShopPotionPolicy.PotionShopSlot selected = AgentShopPotionPolicy.selectPotionItem(
                List.of(huge, smaller), 1000, true, effects::containsKey, effects::get);

        assertEquals(1, selected.slot());
        assertEquals(smaller, selected.shopItem());
    }

    @Test
    void shouldFilterFreeNonRecoveryPercentAndWrongResourceItems() {
        ShopItem free = shopItem(2000, 0);
        ShopItem nonRecovery = shopItem(2001, 100);
        ShopItem percentHp = shopItem(2002, 100);
        ShopItem hpPotion = shopItem(2003, 100);
        Map<Integer, StatEffect> effects = Map.of(
                2000, effect(250, 0, 0, 0),
                2001, effect(250, 0, 0, 0),
                2002, effect(250, 0, 1, 0),
                2003, effect(250, 0, 0, 0));

        AgentShopPotionPolicy.PotionShopSlot selected = AgentShopPotionPolicy.selectPotionItem(
                List.of(free, nonRecovery, percentHp, hpPotion),
                1000,
                true,
                itemId -> itemId != 2001,
                effects::get);

        assertEquals(3, selected.slot());
        assertEquals(hpPotion, selected.shopItem());

        assertNull(AgentShopPotionPolicy.selectPotionItem(
                List.of(hpPotion), 1000, false, effects::containsKey, effects::get));
    }

    private static ShopItem shopItem(int itemId, int price) {
        ShopItem item = mock(ShopItem.class);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getPrice()).thenReturn(price);
        return item;
    }

    private static StatEffect effect(int hp, int mp, int hpRate, int mpRate) {
        StatEffect effect = mock(StatEffect.class);
        when(effect.getHp()).thenReturn((short) hp);
        when(effect.getMp()).thenReturn((short) mp);
        when(effect.getHpRate()).thenReturn((double) hpRate);
        when(effect.getMpRate()).thenReturn((double) mpRate);
        return effect;
    }
}
