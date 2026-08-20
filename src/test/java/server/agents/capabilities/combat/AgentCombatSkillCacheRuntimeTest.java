package server.agents.capabilities.combat;

import client.BuffStat;
import client.Character;
import client.Job;
import client.Skill;
import client.inventory.WeaponType;
import constants.skills.DragonKnight;
import constants.skills.Pirate;
import constants.skills.Rogue;
import constants.skills.Spearman;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import server.StatEffect;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCombatSkillCacheRuntimeTest {
    @Test
    void weaponChangesInvalidateCacheAndExposeOnlyCompatiblePirateSkills() {
        Character bot = learnedBot(Job.PIRATE,
                attackSkill(Pirate.FLASH_FIST, 220),
                attackSkill(Pirate.SOMERSAULT_KICK, 120),
                attackSkill(Pirate.DOUBLE_SHOT, 180));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AtomicReference<WeaponType> weapon = new AtomicReference<>(WeaponType.GUN);

        try (MockedStatic<AgentAttackExecutionProvider> execution = Mockito.mockStatic(
                AgentAttackExecutionProvider.class, Mockito.CALLS_REAL_METHODS)) {
            execution.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot))
                    .thenAnswer(invocation -> weapon.get());

            AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);
            assertFalse(AgentCombatSkillCacheStateRuntime.attackSkillIds(entry).contains(Pirate.FLASH_FIST));
            assertTrue(AgentCombatSkillCacheStateRuntime.attackSkillIds(entry).contains(Pirate.SOMERSAULT_KICK));
            assertTrue(AgentCombatSkillCacheStateRuntime.attackSkillIds(entry).contains(Pirate.DOUBLE_SHOT));

            weapon.set(WeaponType.KNUCKLE);
            AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);
            assertTrue(AgentCombatSkillCacheStateRuntime.attackSkillIds(entry).contains(Pirate.FLASH_FIST));
            assertTrue(AgentCombatSkillCacheStateRuntime.attackSkillIds(entry).contains(Pirate.SOMERSAULT_KICK));
            assertFalse(AgentCombatSkillCacheStateRuntime.attackSkillIds(entry).contains(Pirate.DOUBLE_SHOT));
        }
    }

    @Test
    void sinDitAndSpearPolearmHybridsFollowTheEquippedWeapon() {
        assertCacheFollowsWeapon(
                learnedBot(Job.THIEF,
                        attackSkill(Rogue.LUCKY_SEVEN, 180),
                        attackSkill(Rogue.DOUBLE_STAB, 160)),
                WeaponType.CLAW, Rogue.LUCKY_SEVEN, Rogue.DOUBLE_STAB,
                WeaponType.DAGGER_THIEVES, Rogue.DOUBLE_STAB, Rogue.LUCKY_SEVEN);

        assertCacheFollowsWeapon(
                learnedBot(Job.DRAGONKNIGHT,
                        attackSkill(DragonKnight.SPEAR_CRUSHER, 180),
                        attackSkill(DragonKnight.POLE_ARM_CRUSHER, 180)),
                WeaponType.SPEAR_STAB, DragonKnight.SPEAR_CRUSHER, DragonKnight.POLE_ARM_CRUSHER,
                WeaponType.POLE_ARM_SWING, DragonKnight.POLE_ARM_CRUSHER, DragonKnight.SPEAR_CRUSHER);
    }

    @Test
    void equippedWeaponContributesToTheCacheSignature() {
        int learnedSkills = 12345;
        assertNotEquals(
                AgentCombatSkillCacheRuntime.equipmentAwareSignature(learnedSkills, WeaponType.CLAW),
                AgentCombatSkillCacheRuntime.equipmentAwareSignature(learnedSkills, WeaponType.DAGGER_THIEVES));
    }

    @Test
    void hybridWeaponBoostersAreFilteredAndRebuiltWithTheWeapon() {
        Character bot = learnedBot(Job.SPEARMAN,
                supportSkill(Spearman.SPEAR_BOOSTER),
                supportSkill(Spearman.POLEARM_BOOSTER));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AtomicReference<WeaponType> weapon = new AtomicReference<>(WeaponType.SPEAR_STAB);

        try (MockedStatic<AgentAttackExecutionProvider> execution = Mockito.mockStatic(
                AgentAttackExecutionProvider.class, Mockito.CALLS_REAL_METHODS)) {
            execution.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot))
                    .thenAnswer(invocation -> weapon.get());

            AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);
            assertTrue(AgentCombatSkillCacheStateRuntime.buffSkillIds(entry).contains(Spearman.SPEAR_BOOSTER));
            assertFalse(AgentCombatSkillCacheStateRuntime.buffSkillIds(entry).contains(Spearman.POLEARM_BOOSTER));

            weapon.set(WeaponType.POLE_ARM_SWING);
            AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);
            assertFalse(AgentCombatSkillCacheStateRuntime.buffSkillIds(entry).contains(Spearman.SPEAR_BOOSTER));
            assertTrue(AgentCombatSkillCacheStateRuntime.buffSkillIds(entry).contains(Spearman.POLEARM_BOOSTER));
        }
    }

    private static void assertCacheFollowsWeapon(Character bot,
                                                  WeaponType firstWeapon,
                                                  int firstAllowed,
                                                  int firstBlocked,
                                                  WeaponType secondWeapon,
                                                  int secondAllowed,
                                                  int secondBlocked) {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AtomicReference<WeaponType> weapon = new AtomicReference<>(firstWeapon);
        try (MockedStatic<AgentAttackExecutionProvider> execution = Mockito.mockStatic(
                AgentAttackExecutionProvider.class, Mockito.CALLS_REAL_METHODS)) {
            execution.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot))
                    .thenAnswer(invocation -> weapon.get());

            AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);
            assertTrue(AgentCombatSkillCacheStateRuntime.attackSkillIds(entry).contains(firstAllowed));
            assertFalse(AgentCombatSkillCacheStateRuntime.attackSkillIds(entry).contains(firstBlocked));

            weapon.set(secondWeapon);
            AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);
            assertTrue(AgentCombatSkillCacheStateRuntime.attackSkillIds(entry).contains(secondAllowed));
            assertFalse(AgentCombatSkillCacheStateRuntime.attackSkillIds(entry).contains(secondBlocked));
        }
    }

    private static Character learnedBot(Job job, Skill... skills) {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(job);
        when(bot.getLevel()).thenReturn(100);
        Map<Skill, Character.SkillEntry> learned = new LinkedHashMap<>();
        for (Skill skill : skills) {
            learned.put(skill, null);
        }
        when(bot.getSkills()).thenReturn(learned);
        doAnswer(invocation -> learned.containsKey(invocation.getArgument(0)) ? (byte) 1 : (byte) 0)
                .when(bot).getSkillLevel(any(Skill.class));
        return bot;
    }

    private static Skill attackSkill(int skillId, int damage) {
        Skill skill = new Skill(skillId);
        StatEffect effect = mock(StatEffect.class);
        when(effect.getAttackCount()).thenReturn(1);
        when(effect.getMobCount()).thenReturn(1);
        when(effect.getDamage()).thenReturn(damage);
        when(effect.getDamagePercent()).thenReturn(damage);
        when(effect.hasDamage()).thenReturn(true);
        when(effect.getMpCon()).thenReturn((short) 1);
        skill.addLevelEffect(effect);
        return skill;
    }

    private static Skill supportSkill(int skillId) {
        Skill skill = new Skill(skillId);
        skill.setAction(true);
        StatEffect effect = mock(StatEffect.class);
        when(effect.isOverTime()).thenReturn(true);
        when(effect.getDuration()).thenReturn(120_000);
        when(effect.getStatups()).thenReturn(List.of(new tools.Pair<>(BuffStat.BOOSTER, -2)));
        skill.addLevelEffect(effect);
        return skill;
    }
}
