package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import client.Character;
import constants.skills.Cleric;
import constants.skills.SuperGM;
import org.junit.jupiter.api.Test;

class AgentCombatSupportPolicyTest {
    @Test
    void shouldRejectDragonRoarAtOrBelowHalfHp() {
        Character bot = characterWithHp(100, 200);

        assertFalse(AgentCombatSupportPolicy.canUseDragonRoarPlan(bot, 20, 6, true));
    }

    @Test
    void shouldAllowDragonRoarAboveHalfHpWithEnoughTargets() {
        Character bot = characterWithHp(101, 200);

        assertTrue(AgentCombatSupportPolicy.canUseDragonRoarPlan(bot, 6, 6, false));
    }

    @Test
    void shouldAllowDragonRoarBelowTargetThresholdWithNearbyHealer() {
        Character bot = characterWithHp(101, 200);

        assertTrue(AgentCombatSupportPolicy.canUseDragonRoarPlan(bot, 1, 6, true));
    }

    @Test
    void shouldApplyLegacyHealThreshold() {
        Character healthy = characterWithHp(101, 200);
        Character hurt = characterWithHp(99, 200);

        assertFalse(AgentCombatSupportPolicy.needsHeal(healthy, 0.5));
        assertTrue(AgentCombatSupportPolicy.needsHeal(hurt, 0.5));
    }

    @Test
    void shouldRecognizeClericAndSuperGmHealSkills() {
        Character cleric = mock(Character.class);
        when(cleric.getSkillLevel(Cleric.HEAL)).thenReturn(1);
        Character gm = mock(Character.class);
        when(gm.getSkillLevel(SuperGM.HEAL_PLUS_DISPEL)).thenReturn(1);
        Character other = mock(Character.class);

        assertTrue(AgentCombatSupportPolicy.hasHealSkill(cleric));
        assertTrue(AgentCombatSupportPolicy.hasHealSkill(gm));
        assertFalse(AgentCombatSupportPolicy.hasHealSkill(other));
    }

    private static Character characterWithHp(int hp, int maxHp) {
        Character chr = mock(Character.class);
        when(chr.isAlive()).thenReturn(true);
        when(chr.getHp()).thenReturn(hp);
        when(chr.getCurrentMaxHp()).thenReturn(maxHp);
        return chr;
    }
}
