package server.agents.capabilities.combat;

import client.BuffStat;
import client.Character;
import client.Job;
import client.Skill;
import constants.skills.Assassin;
import constants.skills.Cleric;
import constants.skills.Crusader;
import constants.skills.NightWalker;
import constants.skills.Rogue;
import constants.skills.SuperGM;
import constants.skills.Warrior;
import org.junit.jupiter.api.Test;
import server.StatEffect;
import tools.Pair;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void identifiesBuffBlacklistedSkills() {
        assertTrue(AgentCombatSkillClassifier.isBuffBlacklisted(Rogue.DARK_SIGHT));
        assertTrue(AgentCombatSkillClassifier.isBuffBlacklisted(NightWalker.DARK_SIGHT));
        assertFalse(AgentCombatSkillClassifier.isBuffBlacklisted(Assassin.HASTE));
    }

    @Test
    void identifiesOnlyNonBlacklistedActiveSupportBuffsAsCacheable() {
        Skill haste = skill(Assassin.HASTE, false);
        when(haste.getAction()).thenReturn(true);
        StatEffect support = mock(StatEffect.class);
        when(support.isOverTime()).thenReturn(true);
        when(support.getDuration()).thenReturn(10_000);
        when(support.getStatups()).thenReturn(List.of(new Pair<>(BuffStat.SPEED, 20)));

        Skill darkSight = skill(Rogue.DARK_SIGHT, false);
        when(darkSight.getAction()).thenReturn(true);

        StatEffect passive = mock(StatEffect.class);

        assertTrue(AgentCombatSkillClassifier.isCacheableSupportBuffSkill(haste, support));
        assertFalse(AgentCombatSkillClassifier.isCacheableSupportBuffSkill(darkSight, support));
        assertFalse(AgentCombatSkillClassifier.isCacheableSupportBuffSkill(haste, passive));
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

    @Test
    void classifiesLegacySkillCacheBuckets() {
        Skill heal = skill(Cleric.HEAL, false);
        when(heal.getAction()).thenReturn(true);
        StatEffect healEffect = mock(StatEffect.class);

        Skill attack = skill(Warrior.POWER_STRIKE, false);
        StatEffect attackEffect = mock(StatEffect.class);
        when(attackEffect.hasDamage()).thenReturn(true);
        when(attackEffect.getMpCon()).thenReturn((short) 1);

        Skill summon = skill(Assassin.HASTE + 1000, false);
        StatEffect summonEffect = mock(StatEffect.class);
        when(summonEffect.getStatups()).thenReturn(List.of(new Pair<>(BuffStat.SUMMON, 1)));

        Skill support = skill(Assassin.HASTE, false);
        when(support.getAction()).thenReturn(true);
        StatEffect supportEffect = mock(StatEffect.class);
        when(supportEffect.isOverTime()).thenReturn(true);
        when(supportEffect.getDuration()).thenReturn(10_000);
        when(supportEffect.getStatups()).thenReturn(List.of(new Pair<>(BuffStat.SPEED, 20)));

        Skill darkSight = skill(Rogue.DARK_SIGHT, false);
        when(darkSight.getAction()).thenReturn(true);

        assertEquals(AgentCombatSkillClassifier.SkillCacheBucket.ACTIVE_HEAL,
                AgentCombatSkillClassifier.classifySkillCacheBucket(heal, healEffect));
        assertEquals(AgentCombatSkillClassifier.SkillCacheBucket.ACTIVE_ATTACK,
                AgentCombatSkillClassifier.classifySkillCacheBucket(attack, attackEffect));
        assertEquals(AgentCombatSkillClassifier.SkillCacheBucket.SUMMON,
                AgentCombatSkillClassifier.classifySkillCacheBucket(summon, summonEffect));
        assertEquals(AgentCombatSkillClassifier.SkillCacheBucket.SUPPORT_BUFF,
                AgentCombatSkillClassifier.classifySkillCacheBucket(support, supportEffect));
        assertEquals(AgentCombatSkillClassifier.SkillCacheBucket.IGNORE,
                AgentCombatSkillClassifier.classifySkillCacheBucket(darkSight, supportEffect));
        assertEquals(AgentCombatSkillClassifier.SkillCacheBucket.IGNORE,
                AgentCombatSkillClassifier.classifySkillCacheBucket(null, supportEffect));
    }

    @Test
    void computesSkillCacheSignatureFromLearnedSkillsAndLevels() {
        Character bot = mock(Character.class);
        Skill luckySeven = skill(Rogue.LUCKY_SEVEN, false);
        Skill powerStrike = skill(Warrior.POWER_STRIKE, false);
        Map<Skill, Character.SkillEntry> skills = new LinkedHashMap<>();
        skills.put(luckySeven, new Character.SkillEntry((byte) 7, 0, -1));
        skills.put(null, new Character.SkillEntry((byte) 9, 0, -1));
        skills.put(powerStrike, new Character.SkillEntry((byte) 4, 0, -1));
        when(bot.getSkills()).thenReturn(skills);
        when(bot.getSkillLevel(luckySeven)).thenReturn((byte) 7);
        when(bot.getSkillLevel(powerStrike)).thenReturn((byte) 4);

        int expected = 1;
        expected = 31 * expected + Rogue.LUCKY_SEVEN;
        expected = 31 * expected + 7;
        expected = 31 * expected + Warrior.POWER_STRIKE;
        expected = 31 * expected + 4;

        assertEquals(expected, AgentCombatSkillClassifier.skillCacheSignature(bot));
    }

    @Test
    void scoresSingleTargetSkillPriorityByBeginnerAndJobTree() {
        Character assassin = mock(Character.class);
        when(assassin.getJob()).thenReturn(Job.ASSASSIN);
        Skill beginnerSkill = skill(1000, true);
        Skill inJobSkill = skill(Rogue.LUCKY_SEVEN, false);
        Skill offJobSkill = skill(Warrior.POWER_STRIKE, false);

        assertEquals(0, AgentCombatSkillClassifier.singleTargetSkillPriority(assassin, beginnerSkill));
        assertEquals(2, AgentCombatSkillClassifier.singleTargetSkillPriority(assassin, inJobSkill));
        assertEquals(1, AgentCombatSkillClassifier.singleTargetSkillPriority(assassin, offJobSkill));
        assertEquals(Integer.MIN_VALUE, AgentCombatSkillClassifier.singleTargetSkillPriority(assassin, null));
    }

    @Test
    void selectsBestSingleTargetSkillByPriorityScoreThenLowerSkillId() {
        Character assassin = mock(Character.class);
        when(assassin.getJob()).thenReturn(Job.ASSASSIN);
        Skill luckySeven = skill(Rogue.LUCKY_SEVEN, false);
        Skill powerStrike = skill(Warrior.POWER_STRIKE, false);
        StatEffect strongEffect = mock(StatEffect.class);
        StatEffect weakEffect = mock(StatEffect.class);
        when(strongEffect.getDamagePercent()).thenReturn(150);
        when(weakEffect.getDamagePercent()).thenReturn(100);

        assertTrue(AgentCombatSkillClassifier.shouldUseAsBestSingleTargetSkill(assassin, luckySeven,
                weakEffect, 1, 4, 1, 500, Warrior.POWER_STRIKE));
        assertTrue(AgentCombatSkillClassifier.shouldUseAsBestSingleTargetSkill(assassin, powerStrike,
                strongEffect, 4, 3, 1, 150, Rogue.LUCKY_SEVEN));
        Skill higherIdOffJobSkill = skill(Warrior.POWER_STRIKE + 1, false);
        assertFalse(AgentCombatSkillClassifier.shouldUseAsBestSingleTargetSkill(assassin, higherIdOffJobSkill,
                weakEffect, 1, 1, 1, 100, Warrior.POWER_STRIKE));
    }

    @Test
    void scoresAoeSkillWithLegacyDamageHitAndMobClamp() {
        StatEffect effect = mock(StatEffect.class);
        when(effect.getDamagePercent()).thenReturn(120);

        assertEquals(720, AgentCombatSkillClassifier.aoeSkillScore(effect, 2, 3));
        assertEquals(120, AgentCombatSkillClassifier.aoeSkillScore(effect, 0, 0));

        when(effect.getDamagePercent()).thenReturn(-10);
        assertEquals(0, AgentCombatSkillClassifier.aoeSkillScore(effect, 5, 6));
    }

    @Test
    void shouldPreferCachedAttackSkillListWhenPresent() {
        List<Integer> cached = List.of(1001004, 1001005);

        assertEquals(cached, AgentCombatSkillClassifier.cachedAttackSkillIds(cached, 2001004, 2201005));
    }

    @Test
    void shouldBuildLegacyAttackAndAoeFallbackListWithoutDuplicatesOrZeroes() {
        assertEquals(List.of(1001004, 2201005),
                AgentCombatSkillClassifier.cachedAttackSkillIds(List.of(), 1001004, 2201005));
        assertEquals(List.of(1001004),
                AgentCombatSkillClassifier.cachedAttackSkillIds(List.of(), 1001004, 1001004));
        assertEquals(List.of(2201005),
                AgentCombatSkillClassifier.cachedAttackSkillIds(List.of(), 0, 2201005));
        assertEquals(List.of(),
                AgentCombatSkillClassifier.cachedAttackSkillIds(null, 0, 0));
    }

    private static Skill skill(int skillId, boolean beginner) {
        Skill skill = mock(Skill.class);
        when(skill.getId()).thenReturn(skillId);
        when(skill.isBeginnerSkill()).thenReturn(beginner);
        return skill;
    }
}
