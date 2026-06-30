package server.agents.capabilities.inventory;

import client.BuffStat;
import org.junit.jupiter.api.Test;
import server.StatEffect;
import tools.Pair;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentUseItemClassificationPolicyTest {
    @Test
    void shouldClassifyPureRecoveryPotionsOnlyWhenTheyHealAndHaveNoBuffStatups() {
        assertTrue(AgentUseItemClassificationPolicy.isRecoveryPotion(effect(50, 0, 0, 0, false)));
        assertTrue(AgentUseItemClassificationPolicy.isRecoveryPotion(effect(0, 50, 0, 0, false)));
        assertTrue(AgentUseItemClassificationPolicy.isRecoveryPotion(effect(0, 0, 0.2, 0, false)));
        assertTrue(AgentUseItemClassificationPolicy.isRecoveryPotion(effect(0, 0, 0, 0.2, false)));

        assertFalse(AgentUseItemClassificationPolicy.isRecoveryPotion(effect(0, 0, 0, 0, false)));
        assertFalse(AgentUseItemClassificationPolicy.isRecoveryPotion(effect(50, 0, 0, 0, true)));
        assertFalse(AgentUseItemClassificationPolicy.isRecoveryPotion(null));
    }

    @Test
    void shouldClassifyBuffConsumablesByAnyStatup() {
        assertTrue(AgentUseItemClassificationPolicy.isBuffConsumable(effect(0, 0, 0, 0, true)));
        assertFalse(AgentUseItemClassificationPolicy.isBuffConsumable(effect(50, 0, 0, 0, false)));
        assertFalse(AgentUseItemClassificationPolicy.isBuffConsumable(null));
    }

    @Test
    void shouldLoadItemEffectForItemIdLikeLegacyInventory() {
        StatEffect recovery = effect(50, 0, 0, 0, false);
        assertSame(recovery, AgentUseItemClassificationPolicy.itemEffect(2000000, id -> recovery));
        assertNull(AgentUseItemClassificationPolicy.itemEffect(9999999, id -> {
            throw new RuntimeException("missing");
        }));
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
