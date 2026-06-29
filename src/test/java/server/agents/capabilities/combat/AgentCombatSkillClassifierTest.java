package server.agents.capabilities.combat;

import client.BuffStat;
import client.Skill;
import constants.skills.Assassin;
import constants.skills.Cleric;
import constants.skills.Crusader;
import constants.skills.SuperGM;
import org.junit.jupiter.api.Test;
import server.StatEffect;
import tools.Pair;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCombatSkillClassifierTest {
    @Test
    void identifiesLegacyPartySupportSkills() {
        assertTrue(AgentCombatSkillClassifier.isPartySupportSkill(Assassin.HASTE));
        assertFalse(AgentCombatSkillClassifier.isPartySupportSkill(Crusader.ARMOR_CRASH));
    }

    @Test
    void identifiesHealSkillIdsAndActiveHealEffects() {
        Skill heal = mock(Skill.class);
        StatEffect effect = mock(StatEffect.class);
        when(heal.getAction()).thenReturn(true);

        assertTrue(AgentCombatSkillClassifier.isHealSkill(Cleric.HEAL));
        assertTrue(AgentCombatSkillClassifier.isHealSkill(SuperGM.HEAL_PLUS_DISPEL));
        assertTrue(AgentCombatSkillClassifier.isActiveHealSkill(heal, effect));
        assertFalse(AgentCombatSkillClassifier.isActiveHealSkill(null, effect));
    }

    @Test
    void identifiesSummonStatups() {
        StatEffect summonEffect = mock(StatEffect.class);
        when(summonEffect.getStatups()).thenReturn(List.of(new Pair<>(BuffStat.SUMMON, 1)));

        StatEffect nonSummonEffect = mock(StatEffect.class);
        when(nonSummonEffect.getStatups()).thenReturn(List.of(new Pair<>(BuffStat.WATK, 1)));

        assertTrue(AgentCombatSkillClassifier.isSummonSkill(summonEffect));
        assertFalse(AgentCombatSkillClassifier.isSummonSkill(nonSummonEffect));
        assertFalse(AgentCombatSkillClassifier.isSummonSkill(null));
    }
}
