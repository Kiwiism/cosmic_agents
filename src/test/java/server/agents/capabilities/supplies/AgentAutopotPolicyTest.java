package server.agents.capabilities.supplies;

import client.inventory.Item;
import server.StatEffect;
import server.agents.capabilities.supplies.AgentAutopotPolicy.AutopotChoice;
import server.agents.capabilities.supplies.AgentAutopotPolicy.PotionRanking;
import server.agents.capabilities.supplies.AgentAutopotPolicy.PotionTier;
import tools.Pair;
import client.BuffStat;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentAutopotPolicyTest {
    @Test
    void shouldClassifyPotionRecoveryByLegacyTierOrder() {
        assertEquals(new PotionRanking(PotionTier.FLAT_SINGLE, 50),
                AgentAutopotPolicy.classifyForSlot(effect(50, 0, 0, 0, false), true));
        assertEquals(new PotionRanking(PotionTier.FLAT_MIXED, 50),
                AgentAutopotPolicy.classifyForSlot(effect(50, 20, 0, 0, false), true));
        assertEquals(new PotionRanking(PotionTier.RATE_SINGLE, 0.2),
                AgentAutopotPolicy.classifyForSlot(effect(0, 0, 0.2, 0, false), true));
        assertEquals(new PotionRanking(PotionTier.RATE_MIXED, 0.2),
                AgentAutopotPolicy.classifyForSlot(effect(0, 0, 0.2, 0.1, false), true));
        assertEquals(new PotionRanking(PotionTier.RATE_MIXED, 0.2),
                AgentAutopotPolicy.classifyForSlot(effect(50, 0, 0.2, 0, false), true));
        assertNull(AgentAutopotPolicy.classifyForSlot(effect(0, 20, 0, 0, false), true));
    }

    @Test
    void shouldChooseLowestTierThenSmallestRecoveryValue() {
        Item bigFlatHp = item(2000002, 5);
        Item smallFlatHp = item(2000000, 5);
        Item rateHp = item(2020013, 5);
        Item mp = item(2000003, 5);
        Item buffPotion = item(2001002, 5);
        Item emptyStack = item(2000006, 0);

        Map<Integer, StatEffect> effects = Map.of(
                2000002, effect(300, 0, 0, 0, false),
                2000000, effect(50, 0, 0, 0, false),
                2020013, effect(0, 0, 0.3, 0, false),
                2000003, effect(0, 100, 0, 0, false),
                2001002, effect(100, 100, 0, 0, true),
                2000006, effect(1, 1, 0, 0, false));

        AutopotChoice choice = AgentAutopotPolicy.computeChoice(
                List.of(bigFlatHp, smallFlatHp, rateHp, mp, buffPotion, emptyStack),
                effects::get);

        assertEquals(2000000, choice.hpItemId());
        assertEquals(new PotionRanking(PotionTier.FLAT_SINGLE, 50), choice.hpRank());
        assertEquals(2000003, choice.mpItemId());
        assertEquals(new PotionRanking(PotionTier.FLAT_SINGLE, 100), choice.mpRank());
    }

    private static Item item(int itemId, int quantity) {
        Item item = mock(Item.class);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getQuantity()).thenReturn((short) quantity);
        return item;
    }

    private static StatEffect effect(int hp, int mp, double hpRate, double mpRate, boolean buff) {
        StatEffect effect = mock(StatEffect.class);
        when(effect.getHp()).thenReturn((short) hp);
        when(effect.getMp()).thenReturn((short) mp);
        when(effect.getHpRate()).thenReturn(hpRate);
        when(effect.getMpRate()).thenReturn(mpRate);
        when(effect.getStatups()).thenReturn(buff ? List.of(new Pair<>(BuffStat.WATK, 1)) : List.of());
        return effect;
    }
}
