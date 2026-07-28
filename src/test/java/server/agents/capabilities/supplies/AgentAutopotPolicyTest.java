package server.agents.capabilities.supplies;

import client.BuffStat;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.StatEffect;
import server.agents.capabilities.supplies.AgentAutopotPolicy.AutopotChoice;
import server.agents.capabilities.supplies.AgentAutopotPolicy.AutopotItemChoice;
import tools.Pair;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentAutopotPolicyTest {
    @Test
    void shouldChoosePotionMatchingTheActualDeficit() {
        Item smallFlatHp = item(2000000, 5, 1);
        Item largeFlatHp = item(2000002, 5, 2);
        Item rateHp = item(2020013, 5, 3);
        Item mp = item(2000003, 5, 4);
        Item buffPotion = item(2001002, 5, 5);

        Map<Integer, StatEffect> effects = Map.of(
                2000000, effect(50, 0, 0, 0, false),
                2000002, effect(300, 0, 0, 0, false),
                2020013, effect(0, 0, 0.3, 0, false),
                2000003, effect(0, 100, 0, 0, false),
                2001002, effect(100, 100, 0, 0, true));

        AutopotChoice choice = AgentAutopotPolicy.computeChoice(
                List.of(smallFlatHp, largeFlatHp, rateHp, mp, buffPotion),
                effects::get,
                1000,
                500,
                280,
                100);

        assertEquals(2000002, choice.hpItemId());
        assertEquals((short) 2, choice.hp().position());
        assertEquals(300, choice.hpRank().primaryRecovery());
        assertEquals(2000003, choice.mpItemId());
        assertEquals(100, choice.mpRank().primaryRecovery());
    }

    @Test
    void shouldNormalizePercentageRecoveryAndPreferPurePotionWithinSameBand() {
        Item mixed = item(2000, 3, 1);
        Item purePercent = item(2001, 3, 2);
        Map<Integer, StatEffect> effects = Map.of(
                2000, effect(300, 100, 0, 0, false),
                2001, effect(0, 0, 0.3, 0, false));

        AutopotItemChoice choice = AgentAutopotPolicy.select(
                List.of(mixed, purePercent),
                effects::get,
                1000,
                500,
                300,
                true);

        assertEquals(2001, choice.itemId());
        assertEquals(300, choice.ranking().primaryRecovery());
        assertFalse(choice.ranking().mixed());
        assertTrue(choice.ranking().percentageBased());
    }

    private static Item item(int itemId, int quantity, int position) {
        Item item = mock(Item.class);
        when(item.getItemId()).thenReturn(itemId);
        when(item.getQuantity()).thenReturn((short) quantity);
        when(item.getPosition()).thenReturn((short) position);
        return item;
    }

    private static StatEffect effect(
            int hp, int mp, double hpRate, double mpRate, boolean buff) {
        StatEffect effect = mock(StatEffect.class);
        when(effect.getHp()).thenReturn((short) hp);
        when(effect.getMp()).thenReturn((short) mp);
        when(effect.getHpRate()).thenReturn(hpRate);
        when(effect.getMpRate()).thenReturn(mpRate);
        when(effect.getStatups()).thenReturn(
                buff ? List.of(new Pair<>(BuffStat.WATK, 1)) : List.of());
        return effect;
    }
}
