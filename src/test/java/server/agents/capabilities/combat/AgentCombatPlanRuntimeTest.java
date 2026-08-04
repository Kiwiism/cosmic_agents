package server.agents.capabilities.combat;

import client.Character;
import client.Skill;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import server.StatEffect;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCombatPlanRuntimeTest {
    @Test
    void magicSkillMakesBasicWandAttackAFallbackRatherThanCompetitor() {
        AgentAttackPlan energyBolt = plan(2001004, AgentAttackRoute.MAGIC);
        AgentAttackPlan wandSwing = plan(0, AgentAttackRoute.CLOSE);

        assertTrue(AgentCombatPlanRuntime.hasUsableMagicSkill(List.of(energyBolt, wandSwing)));
    }

    @Test
    void rangedAndCloseSkillsDoNotSuppressBasicFallback() {
        assertFalse(AgentCombatPlanRuntime.hasUsableMagicSkill(List.of(
                plan(3001004, AgentAttackRoute.RANGED),
                plan(1001004, AgentAttackRoute.CLOSE))));
    }

    @Test
    void authoritativeLearnedSkillSnapshotSuppressesWandFallbackWhenAttackCacheIsStale() {
        Character bot = mock(Character.class);
        Skill energyBolt = mock(Skill.class);
        StatEffect effect = mock(StatEffect.class);
        when(energyBolt.getId()).thenReturn(2001004);
        when(energyBolt.getMaxLevel()).thenReturn(20);
        when(energyBolt.getEffect(1)).thenReturn(effect);
        when(effect.hasDamage()).thenReturn(true);
        when(effect.getMpCon()).thenReturn((short) 1);

        assertTrue(AgentCombatPlanRuntime.hasLearnedOffensiveMagicSkill(bot, Map.of(
                energyBolt, new Character.SkillEntry((byte) 1, 0, -1L))));
    }

    @Test
    void passiveMagicianSkillDoesNotSuppressBasicFallback() {
        Character bot = mock(Character.class);
        Skill passive = mock(Skill.class);
        StatEffect effect = mock(StatEffect.class);
        when(passive.getId()).thenReturn(2000000);
        when(passive.getMaxLevel()).thenReturn(16);
        when(passive.getEffect(5)).thenReturn(effect);

        assertFalse(AgentCombatPlanRuntime.hasLearnedOffensiveMagicSkill(bot, Map.of(
                passive, new Character.SkillEntry((byte) 5, 0, -1L))));
    }

    private static AgentAttackPlan plan(int skillId, AgentAttackRoute route) {
        return new AgentAttackPlan(skillId, 1, 1, null, List.of(), route,
                0, 0, 0, 0, 0, 1, 1, WeaponType.WAND);
    }
}
