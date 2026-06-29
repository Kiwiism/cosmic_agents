package server.agents.capabilities.supplies;

import client.BuffStat;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.StatEffect;
import tools.Pair;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPotionInventoryPolicyTest {
    @Test
    void shouldCountPureHpMpAndDualRecoveryPotionStacks() {
        Item hpPotion = item(2000002, 10);
        Item mpPotion = item(2000003, 7);
        Item dualPotion = item(2000004, 4);
        Item buffPotion = item(2001002, 99);
        Item emptyPotion = item(2000000, 0);
        Item unknown = item(9999999, 10);

        Map<Integer, StatEffect> effects = Map.of(
                2000002, effect(300, 0, 0, 0, false),
                2000003, effect(0, 100, 0, 0, false),
                2000004, effect(0, 0, 0.5, 0.5, false),
                2001002, effect(100, 100, 0, 0, true),
                2000000, effect(50, 0, 0, 0, false));

        assertArrayEquals(new int[]{14, 11}, AgentPotionInventoryPolicy.countPureRecoveryPotions(
                List.of(hpPotion, mpPotion, dualPotion, buffPotion, emptyPotion, unknown),
                effects::get));
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
