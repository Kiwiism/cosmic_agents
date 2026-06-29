package server.agents.capabilities.supplies;

import org.junit.jupiter.api.Test;
import server.StatEffect;

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

    private static StatEffect effect(int hp, int mp, double hpRate, double mpRate) {
        StatEffect effect = mock(StatEffect.class);
        when(effect.getHp()).thenReturn((short) hp);
        when(effect.getMp()).thenReturn((short) mp);
        when(effect.getHpRate()).thenReturn(hpRate);
        when(effect.getMpRate()).thenReturn(mpRate);
        return effect;
    }
}
