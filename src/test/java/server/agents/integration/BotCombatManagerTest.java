package server.agents.integration;

import server.agents.capabilities.movement.AgentClimbStateRuntime;

import server.agents.capabilities.movement.AgentMovementStateRuntime;

import server.agents.capabilities.movement.AgentMovementPhysicsStateRuntime;


import server.agents.runtime.AgentModeStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.movement.AgentMovementPhysicsConfig;
import server.agents.capabilities.movement.AgentMovementKinematicsService;
import server.agents.capabilities.movement.AgentMovementTimers;

import server.agents.capabilities.navigation.AgentNavigationGraph;

import server.agents.capabilities.combat.AgentAttackRoute;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentAttackPlan;
import server.agents.capabilities.combat.AgentBasicAttackPlanRuntime;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentCombatWeaponPolicy;
import server.agents.capabilities.combat.AgentCombatRangePolicy;
import server.agents.capabilities.combat.AgentCombatScoringPolicy;
import server.agents.capabilities.combat.AgentCombatTargetSelector;
import server.agents.capabilities.combat.AgentSkillAttackPlanRuntime;
import server.agents.capabilities.combat.AgentSupportSpecialMovePacketBuilder;

import server.agents.capabilities.movement.AgentMovementProfile;

import client.BuffStat;
import client.Character;
import client.Job;
import client.Skill;
import client.SkillFactory;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.WeaponType;
import constants.game.CharacterStance;
import constants.skills.Archer;
import constants.skills.Assassin;
import constants.skills.Beginner;
import constants.skills.Bowmaster;
import constants.skills.Cleric;
import constants.skills.DragonKnight;
import constants.skills.Hunter;
import constants.skills.ILWizard;
import constants.skills.Magician;
import constants.skills.Rogue;
import constants.skills.Spearman;
import constants.skills.Warrior;
import net.server.channel.handlers.AbstractDealDamageHandler;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import server.StatEffect;
import server.agents.capabilities.combat.AgentCombatActionStateRuntime;
import server.agents.capabilities.combat.AgentCombatAoeRepositionRuntime;
import server.agents.integration.AgentCombatAttackRuntime;
import server.agents.capabilities.combat.AgentCombatBuffStateRuntime;
import server.agents.integration.AgentCombatBuffRuntime;
import server.agents.capabilities.combat.AgentCombatCooldownStateRuntime;
import server.agents.integration.AgentCombatDamageRuntime;
import server.agents.integration.AgentCombatDeathRuntime;
import server.agents.capabilities.combat.AgentCombatFacingRuntime;
import server.agents.capabilities.combat.AgentCombatGroundRuntime;
import server.agents.capabilities.combat.AgentCombatSkillCacheStateRuntime;
import server.agents.capabilities.combat.AgentCombatSkillCacheRuntime;
import server.agents.integration.AgentCombatHealRuntime;
import server.agents.capabilities.combat.AgentCombatPlanRuntime;
import server.agents.integration.AgentCombatTargetRuntime;
import server.agents.runtime.AgentDeathStateRuntime;
import server.agents.capabilities.combat.AgentGrindTargetStateRuntime;
import server.agents.capabilities.combat.AgentMobTouchStateRuntime;
import server.agents.capabilities.combat.AgentMobTouchRuntime;
import server.agents.runtime.AgentPatrolStateRuntime;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.capabilities.combat.AgentSkillBuffDebugStateRuntime;
import server.agents.capabilities.combat.data.AgentAttackDataProvider;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;
import server.life.MonsterStats;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.*;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import tools.HexTool;

class BotCombatManagerTest {
    @Test
    void shouldMatchOpenStoryBasicAttackStanceIds() {
        AgentAttackDataProvider provider = AgentAttackDataProvider.getInstance();
        assertEquals(23, provider.getAttackStanceId("swingO1"));
        assertEquals(11, provider.getAttackStanceId("shoot1"));
        assertEquals(10, provider.getAttackStanceId("shot"));
        assertEquals(0, provider.getAttackStanceId("stand1"));
    }

    @Test
    void shouldUseOpenStoryFallbackAttackGroupsByWeaponType() {
        AgentAttackDataProvider provider = AgentAttackDataProvider.getInstance();
        AgentAttackDataProvider.AttackAnimationSpec gunSpec = provider.getBasicAttackSpec(WeaponType.GUN);
        assertEquals(9, gunSpec.display());
        assertEquals("handgun", gunSpec.primaryAction());

        AgentAttackDataProvider.AttackAnimationSpec bowSpec = provider.getBasicAttackSpec(WeaponType.BOW);
        assertEquals(3, bowSpec.display());
        assertEquals("shoot1", bowSpec.primaryAction());

        AgentAttackDataProvider.AttackAnimationSpec twoHandedSpec = provider.getBasicAttackSpec(WeaponType.SWORD2H);
        assertEquals(5, twoHandedSpec.display());
        assertTrue(twoHandedSpec.actions().contains("swingT1"));
        assertTrue(twoHandedSpec.actions().contains("stabO1"));

        AgentAttackDataProvider.AttackAnimationSpec degenerateBowSpec = provider.getBasicAttackSpec(WeaponType.BOW, true);
        assertEquals(3, degenerateBowSpec.display());
        assertTrue(degenerateBowSpec.actions().contains("swingT1"));
        assertTrue(degenerateBowSpec.actions().contains("swingT3"));
    }

    @Test
    void shouldUseExplicitSkillActionBeforeWeaponFallback() {
        Skill skill = new Skill(3121004);
        skill.setAction0("doublefire");

        String action = AgentAttackExecutionProvider.resolveSkillAttackAction(null, skill, 1, WeaponType.BOW);

        assertEquals("doublefire", action);
    }

    @Test
    void shouldMatchRealMagicGuardSpecialMovePacketLayout() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);

        byte[] packet = AgentSupportSpecialMovePacketBuilder.build(bot, Magician.MAGIC_GUARD, 20, 0x009195A5);

        assertArrayEquals(HexTool.toBytes("5B 00 A5 95 91 00 6A 88 1E 00 14 00 00"), packet);
    }

    @Test
    void shouldMatchRealBlessSpecialMovePacketLayout() {
        Character bot = mockBot(new Point(0x155D, 0x01C6), mock(MapleMap.class), 20_000, null);
        when(bot.isFacingLeft()).thenReturn(true);

        byte[] packet = AgentSupportSpecialMovePacketBuilder.build(bot, Cleric.BLESS, 9, 0x00919AAF);

        assertArrayEquals(HexTool.toBytes("5B 00 AF 9A 91 00 4C 1C 23 00 09 5D 15 C6 01 80 00 00"), packet);
    }

    @Test
    void shouldPreferPowerStrikeOverBeginnerAttackForSingleTargetSlot() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        when(bot.getJob()).thenReturn(Job.WARRIOR);
        when(bot.getLevel()).thenReturn(16);

        Skill threeSnails = skillWithAttack(Beginner.THREE_SNAILS, 1, 1, 40);
        Skill powerStrike = skillWithAttack(Warrior.POWER_STRIKE, 1, 1, 260);
        Skill slashBlast = skillWithAttack(Warrior.SLASH_BLAST, 1, 6, 130);

        Map<Skill, Character.SkillEntry> skills = new LinkedHashMap<>();
        skills.put(threeSnails, null);
        skills.put(powerStrike, null);
        skills.put(slashBlast, null);

        when(bot.getSkills()).thenReturn(skills);
        doAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            if (skill.getId() == threeSnails.getId()) {
                return (byte) 1;
            }
            if (skill.getId() == powerStrike.getId()) {
                return (byte) 1;
            }
            if (skill.getId() == slashBlast.getId()) {
                return (byte) 1;
            }
            return (byte) 0;
        }).when(bot).getSkillLevel(any(Skill.class));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);

        assertEquals(Warrior.POWER_STRIKE, AgentCombatSkillCacheStateRuntime.attackSkillId(entry));
        assertEquals(Warrior.SLASH_BLAST, AgentCombatSkillCacheStateRuntime.aoeSkillId(entry));
    }

    @Test
    void shouldNotTreatMpEaterAsActiveAttackSkill() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        when(bot.getJob()).thenReturn(Job.IL_WIZARD);
        when(bot.getLevel()).thenReturn(35);

        Skill thunderbolt = skillWithAttack(ILWizard.THUNDERBOLT, 1, 6, 115);
        Skill mpEater = passiveOverTimeSkillWithCombatMetadata(ILWizard.MP_EATER, 115, 1, 1);

        Map<Skill, Character.SkillEntry> skills = new LinkedHashMap<>();
        skills.put(mpEater, null);
        skills.put(thunderbolt, null);
        when(bot.getSkills()).thenReturn(skills);
        doAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            return (byte) (skill.getId() == ILWizard.MP_EATER || skill.getId() == ILWizard.THUNDERBOLT ? 1 : 0);
        }).when(bot).getSkillLevel(any(Skill.class));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);

        assertEquals(ILWizard.THUNDERBOLT, AgentCombatSkillCacheStateRuntime.aoeSkillId(entry));
        assertEquals(0, AgentCombatSkillCacheStateRuntime.attackSkillId(entry));
        assertFalse(AgentCombatSkillCacheStateRuntime.buffSkillIds(entry).contains(ILWizard.MP_EATER));
    }

    @Test
    void shouldCacheActionBackedSupportBuffButNotPassiveOverTimeSkill() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        when(bot.getJob()).thenReturn(Job.CLERIC);
        when(bot.getLevel()).thenReturn(35);

        Skill bless = skillWithBuffAction(Cleric.BLESS);
        Skill mpEater = passiveOverTimeSkillWithCombatMetadata(Cleric.MP_EATER, 0, 1, 1);

        Map<Skill, Character.SkillEntry> skills = new LinkedHashMap<>();
        skills.put(mpEater, null);
        skills.put(bless, null);
        when(bot.getSkills()).thenReturn(skills);
        when(bot.getSkillLevel(any(Skill.class))).thenReturn((byte) 1);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);

        assertFalse(AgentCombatSkillCacheStateRuntime.buffSkillIds(entry).contains(Cleric.MP_EATER));
        assertTrue(AgentCombatSkillCacheStateRuntime.buffSkillIds(entry).contains(Cleric.BLESS));
    }

    @Test
    void shouldClassifySummonIntoOwnBucketNotBuffs() {
        SkillFactory.loadAllSkills();
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        when(bot.getJob()).thenReturn(Job.BOWMASTER);
        when(bot.getLevel()).thenReturn(120);

        // Phoenix is a CIRCLE_FOLLOW summon (SUMMON statup). Sharp Eyes is a real party buff.
        Set<Integer> skillIds = Set.of(Bowmaster.PHOENIX, Bowmaster.SHARP_EYES);
        Map<Skill, Character.SkillEntry> skills = new LinkedHashMap<>();
        for (int skillId : skillIds) {
            Skill skill = SkillFactory.getSkill(skillId);
            assertTrue(skill != null, "missing real WZ skill " + skillId);
            skills.put(skill, null);
        }
        when(bot.getSkills()).thenReturn(skills);
        doAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            return (byte) (skillIds.contains(skill.getId()) ? 1 : 0);
        }).when(bot).getSkillLevel(any(Skill.class));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);

        assertTrue(AgentCombatSkillCacheStateRuntime.summonSkillIds(entry).contains(Bowmaster.PHOENIX));
        assertFalse(AgentCombatSkillCacheStateRuntime.buffSkillIds(entry).contains(Bowmaster.PHOENIX));
        assertTrue(AgentCombatSkillCacheStateRuntime.buffSkillIds(entry).contains(Bowmaster.SHARP_EYES));
    }

    @Test
    void shouldStillCacheMagicClawAsActiveAttackSkill() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        when(bot.getJob()).thenReturn(Job.MAGICIAN);
        when(bot.getLevel()).thenReturn(18);

        Skill magicClaw = skillWithAttack(Magician.MAGIC_CLAW, 2, 1, 40);
        Skill fakePassive = passiveSkillWithCombatMetadata(ILWizard.MP_EATER, 115, 1, 1);

        Map<Skill, Character.SkillEntry> skills = new LinkedHashMap<>();
        skills.put(fakePassive, null);
        skills.put(magicClaw, null);
        when(bot.getSkills()).thenReturn(skills);
        doAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            return (byte) (skill.getId() == ILWizard.MP_EATER || skill.getId() == Magician.MAGIC_CLAW ? 1 : 0);
        }).when(bot).getSkillLevel(any(Skill.class));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);

        assertEquals(Magician.MAGIC_CLAW, AgentCombatSkillCacheStateRuntime.attackSkillId(entry));
    }

    @Test
    void shouldStillCacheDoubleShotAsActiveAttackSkill() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        when(bot.getJob()).thenReturn(Job.BOWMAN);
        when(bot.getLevel()).thenReturn(12);

        Skill doubleShot = skillWithAttack(Archer.DOUBLE_SHOT, 2, 1, 130);
        Skill fakePassive = passiveSkillWithCombatMetadata(Archer.CRITICAL_SHOT, 200, 1, 1);

        Map<Skill, Character.SkillEntry> skills = new LinkedHashMap<>();
        skills.put(fakePassive, null);
        skills.put(doubleShot, null);
        when(bot.getSkills()).thenReturn(skills);
        doAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            return (byte) (skill.getId() == Archer.CRITICAL_SHOT || skill.getId() == Archer.DOUBLE_SHOT ? 1 : 0);
        }).when(bot).getSkillLevel(any(Skill.class));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);

        assertEquals(Archer.DOUBLE_SHOT, AgentCombatSkillCacheStateRuntime.attackSkillId(entry));
    }

    @Test
    void shouldStillCacheLuckySevenAsActiveAttackSkill() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        when(bot.getJob()).thenReturn(Job.THIEF);
        when(bot.getLevel()).thenReturn(14);

        Skill luckySeven = skillWithAttack(Rogue.LUCKY_SEVEN, 2, 1, 150);
        Skill fakePassive = passiveSkillWithCombatMetadata(Archer.CRITICAL_SHOT, 200, 1, 1);

        Map<Skill, Character.SkillEntry> skills = new LinkedHashMap<>();
        skills.put(fakePassive, null);
        skills.put(luckySeven, null);
        when(bot.getSkills()).thenReturn(skills);
        doAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            return (byte) (skill.getId() == Archer.CRITICAL_SHOT || skill.getId() == Rogue.LUCKY_SEVEN ? 1 : 0);
        }).when(bot).getSkillLevel(any(Skill.class));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);

        assertEquals(Rogue.LUCKY_SEVEN, AgentCombatSkillCacheStateRuntime.attackSkillId(entry));
    }

    @Test
    void shouldStillCacheThunderboltAsActiveAttackSkill() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        when(bot.getJob()).thenReturn(Job.IL_WIZARD);
        when(bot.getLevel()).thenReturn(35);

        Skill thunderbolt = skillWithAttack(ILWizard.THUNDERBOLT, 1, 6, 115);
        Skill fakePassive = passiveSkillWithCombatMetadata(ILWizard.MP_EATER, 115, 1, 1);

        Map<Skill, Character.SkillEntry> skills = new LinkedHashMap<>();
        skills.put(fakePassive, null);
        skills.put(thunderbolt, null);
        when(bot.getSkills()).thenReturn(skills);
        doAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            return (byte) (skill.getId() == ILWizard.MP_EATER || skill.getId() == ILWizard.THUNDERBOLT ? 1 : 0);
        }).when(bot).getSkillLevel(any(Skill.class));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);

        assertEquals(ILWizard.THUNDERBOLT, AgentCombatSkillCacheStateRuntime.aoeSkillId(entry));
    }

    @Test
    void shouldIgnorePassiveCombatMetadataWithoutActiveSkillAction() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        when(bot.getJob()).thenReturn(Job.BOWMAN);
        when(bot.getLevel()).thenReturn(12);

        Skill criticalShot = new Skill(Archer.CRITICAL_SHOT);
        StatEffect passiveEffect = mock(StatEffect.class);
        when(passiveEffect.getDamage()).thenReturn(200);
        when(passiveEffect.getAttackCount()).thenReturn(1);
        when(passiveEffect.getBulletCount()).thenReturn((short) 0);
        when(passiveEffect.getMobCount()).thenReturn(1);
        when(passiveEffect.isOverTime()).thenReturn(false);
        criticalShot.addLevelEffect(passiveEffect);

        Map<Skill, Character.SkillEntry> skills = new LinkedHashMap<>();
        skills.put(criticalShot, null);
        when(bot.getSkills()).thenReturn(skills);
        doAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            return (byte) (skill.getId() == Archer.CRITICAL_SHOT ? 1 : 0);
        }).when(bot).getSkillLevel(any(Skill.class));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);

        assertEquals(0, AgentCombatSkillCacheStateRuntime.attackSkillId(entry));
        assertEquals(0, AgentCombatSkillCacheStateRuntime.aoeSkillId(entry));
        assertTrue(AgentCombatSkillCacheStateRuntime.buffSkillIds(entry).isEmpty());
    }

    @Test
    void shouldIgnoreFinalAttackPassiveDamageMetadata() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        when(bot.getJob()).thenReturn(Job.HUNTER);
        when(bot.getLevel()).thenReturn(35);

        Skill finalAttack = passiveSkillWithCombatMetadata(Hunter.FINAL_ATTACK, 105, 1, 1);
        finalAttack.setSkillType(3);

        Map<Skill, Character.SkillEntry> skills = new LinkedHashMap<>();
        skills.put(finalAttack, null);
        when(bot.getSkills()).thenReturn(skills);
        when(bot.getSkillLevel(any(Skill.class))).thenReturn((byte) 1);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);

        assertEquals(0, AgentCombatSkillCacheStateRuntime.attackSkillId(entry));
        assertEquals(0, AgentCombatSkillCacheStateRuntime.aoeSkillId(entry));
        assertTrue(AgentCombatSkillCacheStateRuntime.buffSkillIds(entry).isEmpty());
    }

    @Test
    void shouldClassifyInspectedSecondJobSkillsFromRealWzData() {
        SkillFactory.loadAllSkills();

        // Slow is a mob-targeting debuff (mobCount + bbox, no caster statup), not a rebuffable
        // self-buff: the bot only casts buffs via a self/party SPECIAL_MOVE, so it is excluded.
        assertRealWzCache(Job.IL_WIZARD, 35,
                Set.of(ILWizard.MP_EATER, ILWizard.MEDITATION, ILWizard.SLOW, ILWizard.COLD_BEAM, ILWizard.THUNDERBOLT),
                ILWizard.COLD_BEAM, ILWizard.THUNDERBOLT,
                Set.of(ILWizard.MEDITATION),
                Set.of(ILWizard.MP_EATER, ILWizard.SLOW));
        assertRealWzCache(Job.CLERIC, 35,
                Set.of(Cleric.MP_EATER, Cleric.HEAL, Cleric.INVINCIBLE, Cleric.BLESS, Cleric.HOLY_ARROW),
                Cleric.HOLY_ARROW, 0,
                Set.of(Cleric.INVINCIBLE, Cleric.BLESS),
                Set.of(Cleric.MP_EATER));
        assertRealWzCache(Job.HUNTER, 35,
                Set.of(Archer.DOUBLE_SHOT, Hunter.FINAL_ATTACK, Hunter.BOW_BOOSTER, Hunter.SOUL_ARROW, Hunter.ARROW_BOMB),
                Archer.DOUBLE_SHOT, Hunter.ARROW_BOMB,
                Set.of(Hunter.BOW_BOOSTER, Hunter.SOUL_ARROW),
                Set.of(Hunter.FINAL_ATTACK));
        assertRealWzCache(Job.ASSASSIN, 35,
                Set.of(Rogue.LUCKY_SEVEN, Assassin.CRITICAL_THROW, Assassin.CLAW_BOOSTER, Assassin.HASTE, Assassin.DRAIN),
                Rogue.LUCKY_SEVEN, 0,
                Set.of(Assassin.CLAW_BOOSTER, Assassin.HASTE),
                Set.of(Assassin.CRITICAL_THROW));
        assertRealWzCache(Job.SPEARMAN, 35,
                Set.of(Warrior.POWER_STRIKE, Warrior.SLASH_BLAST, Spearman.FINAL_ATTACK_SPEAR,
                        Spearman.FINAL_ATTACK_POLEARM, Spearman.SPEAR_BOOSTER, Spearman.IRON_WILL, Spearman.HYPER_BODY),
                Warrior.POWER_STRIKE, Warrior.SLASH_BLAST,
                Set.of(Spearman.SPEAR_BOOSTER, Spearman.IRON_WILL, Spearman.HYPER_BODY),
                Set.of(Spearman.FINAL_ATTACK_SPEAR, Spearman.FINAL_ATTACK_POLEARM));
    }

    @Test
    void shouldCacheDragonKnightAttackCandidatesButExcludePowerCrash() {
        SkillFactory.loadAllSkills();
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        when(bot.getJob()).thenReturn(Job.DRAGONKNIGHT);
        when(bot.getLevel()).thenReturn(100);

        Set<Integer> skillIds = Set.of(
                DragonKnight.SPEAR_CRUSHER,
                DragonKnight.SPEAR_DRAGON_FURY,
                DragonKnight.DRAGON_ROAR,
                DragonKnight.POWER_CRASH,
                DragonKnight.DRAGON_BLOOD);
        Map<Skill, Character.SkillEntry> skills = new LinkedHashMap<>();
        for (int skillId : skillIds) {
            Skill skill = SkillFactory.getSkill(skillId);
            assertTrue(skill != null, "missing real WZ skill " + skillId);
            skills.put(skill, null);
        }
        when(bot.getSkills()).thenReturn(skills);
        doAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            return (byte) (skillIds.contains(skill.getId()) ? 1 : 0);
        }).when(bot).getSkillLevel(any(Skill.class));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);

        assertTrue(AgentCombatSkillCacheStateRuntime.attackSkillIds(entry).contains(DragonKnight.SPEAR_CRUSHER));
        assertTrue(AgentCombatSkillCacheStateRuntime.attackSkillIds(entry).contains(DragonKnight.SPEAR_DRAGON_FURY));
        assertTrue(AgentCombatSkillCacheStateRuntime.attackSkillIds(entry).contains(DragonKnight.DRAGON_ROAR));
        assertFalse(AgentCombatSkillCacheStateRuntime.attackSkillIds(entry).contains(DragonKnight.POWER_CRASH));
        assertFalse(AgentCombatSkillCacheStateRuntime.buffSkillIds(entry).contains(DragonKnight.POWER_CRASH));
        assertTrue(AgentCombatSkillCacheStateRuntime.buffSkillIds(entry).contains(DragonKnight.DRAGON_BLOOD));
    }

    // Regression: Teleport's WZ omits the "damage" attribute, so StatEffect's loader
    // defaults damage to 100. Before hasDamage() was plumbed through, isActiveAttackSkill
    // accepted Teleport as the bot's attack skill on a mid-build I/L Wizard that hadn't
    // learned Cold Beam yet, producing illegal 1-damage magic attacks at the WZ bbox
    // (500x300 around the bot).
    @Test
    void shouldNotPickTeleportAsAttackSkillJustBecauseDamageDefaults() {
        SkillFactory.loadAllSkills();
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        when(bot.getJob()).thenReturn(Job.IL_WIZARD);
        when(bot.getLevel()).thenReturn(40);

        Set<Integer> skillIds = Set.of(ILWizard.TELEPORT, ILWizard.MEDITATION);
        Map<Skill, Character.SkillEntry> skills = new LinkedHashMap<>();
        for (int skillId : skillIds) {
            Skill skill = SkillFactory.getSkill(skillId);
            assertTrue(skill != null, "missing real WZ skill " + skillId);
            skills.put(skill, null);
        }
        when(bot.getSkills()).thenReturn(skills);
        doAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            return (byte) (skillIds.contains(skill.getId()) ? 1 : 0);
        }).when(bot).getSkillLevel(any(Skill.class));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);

        assertEquals(0, AgentCombatSkillCacheStateRuntime.attackSkillId(entry));
        assertFalse(AgentCombatSkillCacheStateRuntime.attackSkillIds(entry).contains(ILWizard.TELEPORT));
    }

    // Regression: Hunter.ARROW_BOMB declares no "damage" in WZ; the damage % lives in "x"
    // (72 at level 1). getDamagePercent() must fall back to x instead of returning the
    // loader-default 100, otherwise Arrow Bomb deals base weapon damage and the AoE
    // scorer over-weights it as a 100% skill.
    @Test
    void arrowBombShouldDeriveDamagePercentFromXNotLoaderDefault() {
        SkillFactory.loadAllSkills();
        Skill arrowBomb = SkillFactory.getSkill(Hunter.ARROW_BOMB);
        assertTrue(arrowBomb != null, "missing real WZ skill Hunter.ARROW_BOMB");
        StatEffect lvl1 = arrowBomb.getEffect(1);
        assertFalse(lvl1.hasDamage(), "Arrow Bomb WZ must not declare 'damage'");
        assertEquals(72, lvl1.getDamagePercent(),
                "level-1 'x' = 72 should be returned as damage %");
    }

    @Test
    void shouldNotUseDragonRoarBelowTargetThresholdWithoutNearbyHealer() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        when(bot.calculateMaxBaseDamage(100)).thenReturn(20);
        when(bot.calculateMinBaseDamage(100, 0.1d)).thenReturn(10);
        Skill cheaperAoe = skillWithAttackBox(Warrior.SLASH_BLAST, 1, 6, 200,
                new Rectangle(80, 150, 220, 100));
        Skill roar = skillWithAttackBox(DragonKnight.DRAGON_ROAR, 1, 15, 240,
                new Rectangle(-300, -200, 800, 500));
        List<Monster> mobs = List.of(
                mockMob(new Point(140, 200), 9300300),
                mockMob(new Point(150, 200), 9300301),
                mockMob(new Point(160, 200), 9300302));
        when(map.getAllMonsters()).thenReturn(mobs);
        when(bot.getSkillLevel(any(Skill.class))).thenReturn((byte) 1);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheStateRuntime.addAttackSkillId(entry, cheaperAoe.getId());
        AgentCombatSkillCacheStateRuntime.addAttackSkillId(entry, roar.getId());

        try (MockedStatic<SkillFactory> skillFactory = Mockito.mockStatic(SkillFactory.class)) {
            skillFactory.when(() -> SkillFactory.getSkill(cheaperAoe.getId())).thenReturn(cheaperAoe);
            skillFactory.when(() -> SkillFactory.getSkill(roar.getId())).thenReturn(roar);

            AgentAttackPlan plan = AgentCombatPlanRuntime.planAttack(entry, bot, mobs.get(0), AgentCombatConfig.cfg);

            assertEquals(Warrior.SLASH_BLAST, plan.skillId);
        }
    }

    @Test
    void shouldUseDragonRoarWhenLargeClusterMeetsTargetThreshold() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        when(bot.calculateMaxBaseDamage(100)).thenReturn(20);
        when(bot.calculateMinBaseDamage(100, 0.1d)).thenReturn(10);
        Skill cheaperAoe = skillWithAttackBox(Warrior.SLASH_BLAST, 1, 6, 200,
                new Rectangle(80, 150, 220, 100));
        Skill roar = skillWithAttackBox(DragonKnight.DRAGON_ROAR, 1, 15, 240,
                new Rectangle(-300, -200, 800, 500));
        List<Monster> mobs = List.of(
                mockMob(new Point(140, 200), 9300400),
                mockMob(new Point(150, 200), 9300401),
                mockMob(new Point(160, 200), 9300402),
                mockMob(new Point(170, 200), 9300403),
                mockMob(new Point(180, 200), 9300404),
                mockMob(new Point(190, 200), 9300405),
                mockMob(new Point(200, 200), 9300406),
                mockMob(new Point(210, 200), 9300407),
                mockMob(new Point(220, 200), 9300408),
                mockMob(new Point(230, 200), 9300409));
        when(map.getAllMonsters()).thenReturn(mobs);
        when(bot.getSkillLevel(any(Skill.class))).thenReturn((byte) 1);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheStateRuntime.addAttackSkillId(entry, cheaperAoe.getId());
        AgentCombatSkillCacheStateRuntime.addAttackSkillId(entry, roar.getId());

        try (MockedStatic<SkillFactory> skillFactory = Mockito.mockStatic(SkillFactory.class)) {
            skillFactory.when(() -> SkillFactory.getSkill(cheaperAoe.getId())).thenReturn(cheaperAoe);
            skillFactory.when(() -> SkillFactory.getSkill(roar.getId())).thenReturn(roar);

            AgentAttackPlan plan = AgentCombatPlanRuntime.planAttack(entry, bot, mobs.get(0), AgentCombatConfig.cfg);

            assertEquals(DragonKnight.DRAGON_ROAR, plan.skillId);
            assertEquals(10, plan.targets.size());
        }
    }

    @Test
    void shouldAllowDragonRoarBelowTargetThresholdWhenNearbyHealerAndDamageWins() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Character healer = mockBot(new Point(120, 200), map, 20_000, null);
        when(healer.getId()).thenReturn(2);
        when(healer.getSkillLevel(Cleric.HEAL)).thenReturn(1);
        when(bot.getPartyMembersOnSameMap()).thenReturn(List.of(healer));
        when(bot.calculateMaxBaseDamage(100)).thenReturn(20);
        when(bot.calculateMinBaseDamage(100, 0.1d)).thenReturn(10);
        Skill cheaperAoe = skillWithAttackBox(Warrior.SLASH_BLAST, 1, 6, 100,
                new Rectangle(80, 150, 220, 100));
        Skill roar = skillWithAttackBox(DragonKnight.DRAGON_ROAR, 1, 15, 240,
                new Rectangle(-300, -200, 800, 500));
        List<Monster> mobs = List.of(
                mockMob(new Point(140, 200), 9300410),
                mockMob(new Point(150, 200), 9300411),
                mockMob(new Point(160, 200), 9300412));
        when(map.getAllMonsters()).thenReturn(mobs);
        when(bot.getSkillLevel(any(Skill.class))).thenReturn((byte) 1);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheStateRuntime.addAttackSkillId(entry, cheaperAoe.getId());
        AgentCombatSkillCacheStateRuntime.addAttackSkillId(entry, roar.getId());

        try (MockedStatic<SkillFactory> skillFactory = Mockito.mockStatic(SkillFactory.class)) {
            skillFactory.when(() -> SkillFactory.getSkill(cheaperAoe.getId())).thenReturn(cheaperAoe);
            skillFactory.when(() -> SkillFactory.getSkill(roar.getId())).thenReturn(roar);

            AgentAttackPlan plan = AgentCombatPlanRuntime.planAttack(entry, bot, mobs.get(0), AgentCombatConfig.cfg);

            assertEquals(DragonKnight.DRAGON_ROAR, plan.skillId);
        }
    }

    @Test
    void shouldNotForceDragonRoarWithNearbyHealerWhenAlternativeDamageWins() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Character healer = mockBot(new Point(120, 200), map, 20_000, null);
        when(healer.getId()).thenReturn(2);
        when(healer.getSkillLevel(Cleric.HEAL)).thenReturn(1);
        when(bot.getPartyMembersOnSameMap()).thenReturn(List.of(healer));
        when(bot.calculateMaxBaseDamage(100)).thenReturn(20);
        when(bot.calculateMinBaseDamage(100, 0.1d)).thenReturn(10);
        Skill cheaperAoe = skillWithAttackBox(Warrior.SLASH_BLAST, 1, 6, 500,
                new Rectangle(80, 150, 220, 100));
        Skill roar = skillWithAttackBox(DragonKnight.DRAGON_ROAR, 1, 15, 10,
                new Rectangle(-300, -200, 800, 500));
        List<Monster> mobs = List.of(
                mockMob(new Point(140, 200), 9300420),
                mockMob(new Point(150, 200), 9300421),
                mockMob(new Point(160, 200), 9300422));
        when(map.getAllMonsters()).thenReturn(mobs);
        when(bot.getSkillLevel(any(Skill.class))).thenReturn((byte) 1);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheStateRuntime.addAttackSkillId(entry, cheaperAoe.getId());
        AgentCombatSkillCacheStateRuntime.addAttackSkillId(entry, roar.getId());

        try (MockedStatic<SkillFactory> skillFactory = Mockito.mockStatic(SkillFactory.class)) {
            skillFactory.when(() -> SkillFactory.getSkill(cheaperAoe.getId())).thenReturn(cheaperAoe);
            skillFactory.when(() -> SkillFactory.getSkill(roar.getId())).thenReturn(roar);

            AgentAttackPlan plan = AgentCombatPlanRuntime.planAttack(entry, bot, mobs.get(0), AgentCombatConfig.cfg);

            assertEquals(Warrior.SLASH_BLAST, plan.skillId);
        }
    }

    @Test
    void shouldNotUseDragonRoarAtOrBelowHalfHp() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 10_000, null);
        when(bot.getCurrentMaxHp()).thenReturn(20_000);
        when(bot.calculateMaxBaseDamage(100)).thenReturn(20);
        when(bot.calculateMinBaseDamage(100, 0.1d)).thenReturn(10);
        Skill roar = skillWithAttackBox(DragonKnight.DRAGON_ROAR, 1, 15, 240,
                new Rectangle(-300, -200, 800, 500));
        List<Monster> mobs = List.of(
                mockMob(new Point(140, 200), 9300430),
                mockMob(new Point(150, 200), 9300431),
                mockMob(new Point(160, 200), 9300432),
                mockMob(new Point(170, 200), 9300433),
                mockMob(new Point(180, 200), 9300434),
                mockMob(new Point(190, 200), 9300435),
                mockMob(new Point(200, 200), 9300436),
                mockMob(new Point(210, 200), 9300437),
                mockMob(new Point(220, 200), 9300438),
                mockMob(new Point(230, 200), 9300439));
        when(map.getAllMonsters()).thenReturn(mobs);
        when(bot.getSkillLevel(any(Skill.class))).thenReturn((byte) 1);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheStateRuntime.addAttackSkillId(entry, roar.getId());

        try (MockedStatic<SkillFactory> skillFactory = Mockito.mockStatic(SkillFactory.class)) {
            skillFactory.when(() -> SkillFactory.getSkill(roar.getId())).thenReturn(roar);

            AgentAttackPlan plan = AgentCombatPlanRuntime.planAttack(entry, bot, mobs.get(0), AgentCombatConfig.cfg);

            assertEquals(0, plan.skillId);
        }
    }

    @Test
    void shouldSkipPolearmDragonKnightSkillsWhenSpearIsEquipped() {
        assertTrue(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                DragonKnight.SPEAR_CRUSHER, WeaponType.SPEAR_STAB));
        assertTrue(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                DragonKnight.SPEAR_DRAGON_FURY, WeaponType.SPEAR_STAB));
        assertFalse(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                DragonKnight.POLE_ARM_CRUSHER, WeaponType.SPEAR_STAB));
        assertFalse(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                DragonKnight.POLE_ARM_DRAGON_FURY, WeaponType.SPEAR_STAB));
        assertTrue(AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(
                DragonKnight.POLE_ARM_CRUSHER, WeaponType.POLE_ARM_SWING));
    }

    @Test
    void shouldChooseSingleTargetSkillWhenMobDefenseCollapsesLowDamageAoeLines() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Skill powerStrike = skillWithAttack(Warrior.POWER_STRIKE, 1, 1, 260);
        Skill slashBlast = skillWithAttack(Warrior.SLASH_BLAST, 6, 6, 20);
        Monster primary = mockMob(new Point(140, 200), 9300100);
        Monster secondary = mockMob(new Point(150, 200), 9300101);
        when(primary.getWdef()).thenReturn(500);
        when(secondary.getWdef()).thenReturn(500);
        when(map.getAllMonsters()).thenReturn(List.of(primary, secondary));
        doAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            return (byte) (skill.getId() == powerStrike.getId() || skill.getId() == slashBlast.getId() ? 1 : 0);
        }).when(bot).getSkillLevel(any(Skill.class));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheStateRuntime.setAttackSkillId(entry, powerStrike.getId());
        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, slashBlast.getId(), AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry));

        try (MockedStatic<SkillFactory> skillFactory = Mockito.mockStatic(SkillFactory.class)) {
            skillFactory.when(() -> SkillFactory.getSkill(powerStrike.getId())).thenReturn(powerStrike);
            skillFactory.when(() -> SkillFactory.getSkill(slashBlast.getId())).thenReturn(slashBlast);

            AgentAttackPlan plan = AgentCombatPlanRuntime.planAttack(entry, bot, primary, AgentCombatConfig.cfg);

            assertEquals(Warrior.POWER_STRIKE, plan.skillId);
            assertEquals(List.of(primary), plan.targets);
        }
    }

    @Test
    void shouldNotUseWeakAoeOnlyBecauseCurrentHpIsLow() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Skill powerStrike = skillWithAttack(Warrior.POWER_STRIKE, 1, 1, 260);
        Skill slashBlast = skillWithAttack(Warrior.SLASH_BLAST, 1, 6, 120);
        Monster primary = mockMob(new Point(140, 200), 9300200);
        Monster secondary = mockMob(new Point(150, 200), 9300201);
        when(primary.getHp()).thenReturn(100);
        when(map.getAllMonsters()).thenReturn(List.of(primary, secondary));
        doAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            return (byte) (skill.getId() == powerStrike.getId() || skill.getId() == slashBlast.getId() ? 1 : 0);
        }).when(bot).getSkillLevel(any(Skill.class));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheStateRuntime.setAttackSkillId(entry, powerStrike.getId());
        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, slashBlast.getId(), AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry));

        try (MockedStatic<SkillFactory> skillFactory = Mockito.mockStatic(SkillFactory.class)) {
            skillFactory.when(() -> SkillFactory.getSkill(powerStrike.getId())).thenReturn(powerStrike);
            skillFactory.when(() -> SkillFactory.getSkill(slashBlast.getId())).thenReturn(slashBlast);

            AgentAttackPlan plan = AgentCombatPlanRuntime.planAttack(entry, bot, primary, AgentCombatConfig.cfg);

            assertEquals(Warrior.POWER_STRIKE, plan.skillId);
            assertEquals(List.of(primary), plan.targets);
        }
    }

    @Test
    void shouldRepositionToClusterCentroidWhenAoeDpsBeatsSingleTarget() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Skill powerStrike = skillWithAttack(Warrior.POWER_STRIKE, 1, 1, 260);
        // AoE box authored bot-relative: at the bot (x=100) it covers x[20,180], catching only the
        // edge mob; after the bot steps to the centroid the same box (translated) catches all three.
        Skill slashBlast = skillWithAttackBox(Warrior.SLASH_BLAST, 4, 6, 50,
                new Rectangle(20, 170, 160, 60));
        Monster primary = mockMob(new Point(140, 200), 9300500);
        Monster mid = mockMob(new Point(200, 200), 9300501);
        Monster far = mockMob(new Point(260, 200), 9300502);
        when(map.getAllMonsters()).thenReturn(List.of(primary, mid, far));
        doAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            return (byte) (skill.getId() == powerStrike.getId() || skill.getId() == slashBlast.getId() ? 1 : 0);
        }).when(bot).getSkillLevel(any(Skill.class));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheStateRuntime.setAttackSkillId(entry, powerStrike.getId());
        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, slashBlast.getId(), AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, AgentCombatSkillCacheStateRuntime.aoeSkillId(entry), 6);

        try (MockedStatic<SkillFactory> skillFactory = Mockito.mockStatic(SkillFactory.class)) {
            skillFactory.when(() -> SkillFactory.getSkill(powerStrike.getId())).thenReturn(powerStrike);
            skillFactory.when(() -> SkillFactory.getSkill(slashBlast.getId())).thenReturn(slashBlast);

            // At the bot's current position the AoE catches only the edge mob, so the single-target
            // skill wins on DPS — this is the fire-now plan that would otherwise trigger immediately.
            AgentAttackPlan fireNow = AgentCombatPlanRuntime.planAttack(entry, bot, primary, AgentCombatConfig.cfg);
            assertEquals(Warrior.POWER_STRIKE, fireNow.skillId);
            assertEquals(List.of(primary), fireNow.targets);

            Point reposition = AgentCombatAoeRepositionRuntime.aoeRepositionTarget(entry, bot, primary, fireNow, AgentCombatConfig.cfg);
            assertNotNull(reposition, "should defer the single-target shot to step into the cluster");
            assertEquals(200, reposition.x, "should walk to the 3-mob centroid (140+200+260)/3");
        }
    }

    @Test
    void shouldNotRepositionWhenToggleDisabled() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Skill powerStrike = skillWithAttack(Warrior.POWER_STRIKE, 1, 1, 260);
        Skill slashBlast = skillWithAttackBox(Warrior.SLASH_BLAST, 4, 6, 50,
                new Rectangle(20, 170, 160, 60));
        Monster primary = mockMob(new Point(140, 200), 9300540);
        Monster mid = mockMob(new Point(200, 200), 9300541);
        Monster far = mockMob(new Point(260, 200), 9300542);
        when(map.getAllMonsters()).thenReturn(List.of(primary, mid, far));
        doAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            return (byte) (skill.getId() == powerStrike.getId() || skill.getId() == slashBlast.getId() ? 1 : 0);
        }).when(bot).getSkillLevel(any(Skill.class));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheStateRuntime.setAttackSkillId(entry, powerStrike.getId());
        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, slashBlast.getId(), AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, AgentCombatSkillCacheStateRuntime.aoeSkillId(entry), 6);

        boolean original = AgentCombatConfig.cfg.AOE_REPOSITION_ENABLED;
        AgentCombatConfig.cfg.AOE_REPOSITION_ENABLED = false;
        try (MockedStatic<SkillFactory> skillFactory = Mockito.mockStatic(SkillFactory.class)) {
            skillFactory.when(() -> SkillFactory.getSkill(powerStrike.getId())).thenReturn(powerStrike);
            skillFactory.when(() -> SkillFactory.getSkill(slashBlast.getId())).thenReturn(slashBlast);

            AgentAttackPlan fireNow = AgentCombatPlanRuntime.planAttack(entry, bot, primary, AgentCombatConfig.cfg);
            assertNull(AgentCombatAoeRepositionRuntime.aoeRepositionTarget(entry, bot, primary, fireNow, AgentCombatConfig.cfg));
        } finally {
            AgentCombatConfig.cfg.AOE_REPOSITION_ENABLED = original;
        }
    }

    @Test
    void shouldNotRepositionWhenNoAoeSkill() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Skill powerStrike = skillWithAttack(Warrior.POWER_STRIKE, 1, 1, 260);
        Monster primary = mockMob(new Point(140, 200), 9300510);
        Monster mid = mockMob(new Point(200, 200), 9300511);
        Monster far = mockMob(new Point(260, 200), 9300512);
        when(map.getAllMonsters()).thenReturn(List.of(primary, mid, far));
        when(bot.getSkillLevel(any(Skill.class))).thenReturn((byte) 1);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheStateRuntime.setAttackSkillId(entry, powerStrike.getId());
        // No aoeSkillId / aoeSkillMobs left at default 1.

        try (MockedStatic<SkillFactory> skillFactory = Mockito.mockStatic(SkillFactory.class)) {
            skillFactory.when(() -> SkillFactory.getSkill(powerStrike.getId())).thenReturn(powerStrike);

            AgentAttackPlan fireNow = AgentCombatPlanRuntime.planAttack(entry, bot, primary, AgentCombatConfig.cfg);
            assertNull(AgentCombatAoeRepositionRuntime.aoeRepositionTarget(entry, bot, primary, fireNow, AgentCombatConfig.cfg));
        }
    }

    @Test
    void shouldNotRepositionWhenFireNowPlanIsAlreadyAoe() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Monster primary = mockMob(new Point(140, 200), 9300520);
        Monster mid = mockMob(new Point(200, 200), 9300521);
        when(map.getAllMonsters()).thenReturn(List.of(primary, mid));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, Warrior.SLASH_BLAST, AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, AgentCombatSkillCacheStateRuntime.aoeSkillId(entry), 6);

        // Fire-now plan is the AoE itself — nothing to upgrade by repositioning.
        AgentAttackPlan aoePlan = new AgentAttackPlan(
                Warrior.SLASH_BLAST, 1, 1, new Rectangle(20, 170, 160, 60), List.of(primary),
                AgentAttackRoute.CLOSE, 0, 0, 0, 0, 0, 0, 100, null);
        assertNull(AgentCombatAoeRepositionRuntime.aoeRepositionTarget(entry, bot, primary, aoePlan, AgentCombatConfig.cfg));
    }

    @Test
    void shouldNotRepositionForLoneMob() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Skill powerStrike = skillWithAttack(Warrior.POWER_STRIKE, 1, 1, 260);
        Skill slashBlast = skillWithAttackBox(Warrior.SLASH_BLAST, 4, 6, 50,
                new Rectangle(20, 170, 160, 60));
        Monster primary = mockMob(new Point(140, 200), 9300530);
        when(map.getAllMonsters()).thenReturn(List.of(primary));
        doAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            return (byte) (skill.getId() == powerStrike.getId() || skill.getId() == slashBlast.getId() ? 1 : 0);
        }).when(bot).getSkillLevel(any(Skill.class));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheStateRuntime.setAttackSkillId(entry, powerStrike.getId());
        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, slashBlast.getId(), AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, AgentCombatSkillCacheStateRuntime.aoeSkillId(entry), 6);

        try (MockedStatic<SkillFactory> skillFactory = Mockito.mockStatic(SkillFactory.class)) {
            skillFactory.when(() -> SkillFactory.getSkill(powerStrike.getId())).thenReturn(powerStrike);
            skillFactory.when(() -> SkillFactory.getSkill(slashBlast.getId())).thenReturn(slashBlast);

            AgentAttackPlan fireNow = AgentCombatPlanRuntime.planAttack(entry, bot, primary, AgentCombatConfig.cfg);
            assertNull(AgentCombatAoeRepositionRuntime.aoeRepositionTarget(entry, bot, primary, fireNow, AgentCombatConfig.cfg));
        }
    }

    @Test
    void shouldDetectAoeBotSingleTargeting() {
        Monster mob = mockMob(new Point(140, 200), 9300560);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, Warrior.SLASH_BLAST, AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, AgentCombatSkillCacheStateRuntime.aoeSkillId(entry), 6);

        AgentAttackPlan singleTarget = new AgentAttackPlan(
                Warrior.POWER_STRIKE, 1, 1, new Rectangle(0, 0, 1, 1), List.of(mob),
                AgentAttackRoute.CLOSE, 0, 0, 0, 0, 0, 0, 100, null);
        assertTrue(AgentCombatScoringPolicy.isAoeSingleTargeting(
                singleTarget.skillId,
                singleTarget.targets.size(),
                AgentCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                AgentCombatSkillCacheStateRuntime.aoeSkillId(entry),
                AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry)));

        AgentAttackPlan aoePlan = new AgentAttackPlan(
                Warrior.SLASH_BLAST, 1, 1, new Rectangle(0, 0, 1, 1), List.of(mob),
                AgentAttackRoute.CLOSE, 0, 0, 0, 0, 0, 0, 100, null);
        assertFalse(AgentCombatScoringPolicy.isAoeSingleTargeting(
                aoePlan.skillId,
                aoePlan.targets.size(),
                AgentCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                AgentCombatSkillCacheStateRuntime.aoeSkillId(entry),
                AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry)));

        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, 0, AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
        assertFalse(AgentCombatScoringPolicy.isAoeSingleTargeting(
                singleTarget.skillId,
                singleTarget.targets.size(),
                AgentCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                AgentCombatSkillCacheStateRuntime.aoeSkillId(entry),
                AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry)));
    }

    @Test
    void shouldCountAoeClusterSizeCappedAtSkillMobs() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Monster anchor = mockMob(new Point(140, 200), 9300570);
        Monster near1 = mockMob(new Point(180, 200), 9300571);
        Monster near2 = mockMob(new Point(220, 200), 9300572);
        Monster far = mockMob(new Point(600, 200), 9300573); // outside AOE_CLUSTER_RADIUS_PX
        when(map.getAllMonsters()).thenReturn(List.of(anchor, near1, near2, far));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, Warrior.SLASH_BLAST, AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry));
        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, AgentCombatSkillCacheStateRuntime.aoeSkillId(entry), 6);
        assertEquals(3, AgentCombatScoringPolicy.legacyCappedAoeClusterSize(
                anchor,
                bot.getMap().getAllMonsters(),
                AgentCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry)));

        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, AgentCombatSkillCacheStateRuntime.aoeSkillId(entry), 2); // cap below cluster size
        assertEquals(2, AgentCombatScoringPolicy.legacyCappedAoeClusterSize(
                anchor,
                bot.getMap().getAllMonsters(),
                AgentCombatSkillCacheStateRuntime.hasMultiMobAoeSkill(entry),
                AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry)));
    }

    @Test
    void shouldTreatBasicStaffAttacksAsCloseRange() {
        assertEquals(AgentAttackRoute.CLOSE, AgentAttackExecutionProvider.determineBasicWeaponRoute(WeaponType.STAFF));
    }

    @Test
    void shouldTreatNonMageSkillsWithStaffAsCloseRange() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);

        try (MockedStatic<AgentAttackExecutionProvider> attacks =
                     Mockito.mockStatic(AgentAttackExecutionProvider.class, Mockito.CALLS_REAL_METHODS)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.STAFF);

            assertEquals(AgentAttackRoute.CLOSE,
                    AgentAttackExecutionProvider.determineSkillRoute(bot, Warrior.POWER_STRIKE));
            assertEquals(AgentAttackRoute.MAGIC,
                    AgentAttackExecutionProvider.determineSkillRoute(bot, Magician.MAGIC_CLAW));
        }
    }

    @Test
    void shouldUseDpsInsteadOfRawDamageForSlowDragonRoar() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        when(bot.calculateMaxBaseDamage(100)).thenReturn(1_000);
        when(bot.calculateMinBaseDamage(100, 0.1d)).thenReturn(500);
        Skill slashBlast = skillWithAttackBox(Warrior.SLASH_BLAST, 1, 6, 200,
                new Rectangle(80, 150, 220, 100), 500);
        Skill roar = skillWithAttackBox(DragonKnight.DRAGON_ROAR, 1, 15, 240,
                new Rectangle(-300, -200, 800, 500), 4_000);
        List<Monster> mobs = List.of(
                mockMob(new Point(140, 200), 9300440),
                mockMob(new Point(150, 200), 9300441),
                mockMob(new Point(160, 200), 9300442),
                mockMob(new Point(170, 200), 9300443),
                mockMob(new Point(180, 200), 9300444),
                mockMob(new Point(190, 200), 9300445),
                mockMob(new Point(200, 200), 9300446),
                mockMob(new Point(210, 200), 9300447),
                mockMob(new Point(220, 200), 9300448),
                mockMob(new Point(230, 200), 9300449));
        when(map.getAllMonsters()).thenReturn(mobs);
        when(bot.getSkillLevel(any(Skill.class))).thenReturn((byte) 1);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheStateRuntime.addAttackSkillId(entry, slashBlast.getId());
        AgentCombatSkillCacheStateRuntime.addAttackSkillId(entry, roar.getId());

        try (MockedStatic<SkillFactory> skillFactory = Mockito.mockStatic(SkillFactory.class)) {
            skillFactory.when(() -> SkillFactory.getSkill(slashBlast.getId())).thenReturn(slashBlast);
            skillFactory.when(() -> SkillFactory.getSkill(roar.getId())).thenReturn(roar);

            AgentAttackPlan plan = AgentCombatPlanRuntime.planAttack(entry, bot, mobs.get(0), AgentCombatConfig.cfg);

            assertEquals(Warrior.SLASH_BLAST, plan.skillId);
        }
    }

    @Test
    void shouldSkipPartySupportBuffsWhenMapHasNoLivingMobs() {
        MapleMap map = mock(MapleMap.class);
        when(map.getAllMonsters()).thenReturn(List.of());

        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Character ally = mock(Character.class);
        when(ally.getId()).thenReturn(2);
        when(ally.isAlive()).thenReturn(true);
        when(ally.getPosition()).thenReturn(new Point(120, 200));
        when(ally.getBuffedValue(BuffStat.WATK)).thenReturn(null);
        when(bot.getPartyMembersOnSameMap()).thenReturn(List.of(ally));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setFollowing(entry, true);
        AgentCombatSkillCacheStateRuntime.addBuffSkillId(entry, Cleric.BLESS);
        AgentCombatBuffStateRuntime.setNextSupportBuffAt(entry, Cleric.BLESS, 0L);

        Skill bless = new Skill(Cleric.BLESS);
        StatEffect effect = mock(StatEffect.class);
        when(effect.isOverTime()).thenReturn(true);
        when(effect.getStatups()).thenReturn(List.of(new tools.Pair<>(BuffStat.WATK, 10)));
        bless.addLevelEffect(effect);
        when(bot.getSkillLevel(any(Skill.class))).thenReturn((byte) 1);

        try (MockedStatic<SkillFactory> skillFactory = Mockito.mockStatic(SkillFactory.class)) {
            skillFactory.when(() -> SkillFactory.getSkill(Cleric.BLESS)).thenReturn(bless);

            AgentCombatBuffRuntime.tickBuffs(entry, bot, AgentCombatConfig.cfg);
        }

        assertEquals("no skill buff checks yet", AgentSkillBuffDebugStateRuntime.lastActionSummary(entry));
        assertEquals(0L, AgentCombatBuffStateRuntime.nextSupportBuffAt(entry, Cleric.BLESS));
        assertEquals(0, AgentCombatCooldownStateRuntime.attackCooldownMs(entry));
    }

    @Test
    void supportHealRuntimeReturnsFalseWhenNoHealSkillIsCached() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentModeStateRuntime.setFollowing(entry, true);

        assertFalse(AgentCombatHealRuntime.tickSupportHealing(entry, bot, AgentCombatConfig.cfg));
    }

    @Test
    void combatActionStateRuntimeClearsTargetAndActionLocks() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        Monster target = mockMob(new Point(140, 200), 9300504);
        AgentGrindTargetStateRuntime.setTarget(entry, target);
        AgentCombatCooldownStateRuntime.maxAttackCooldown(entry, 500);
        AgentCombatCooldownStateRuntime.maxMoveWindow(entry, 250);

        AgentCombatActionStateRuntime.clearActionState(entry);

        assertNull(AgentGrindTargetStateRuntime.target(entry));
        assertEquals(0, AgentCombatCooldownStateRuntime.attackCooldownMs(entry));
        assertEquals(0, AgentCombatCooldownStateRuntime.moveWindowMs(entry));
    }

    @Test
    void combatAttackRuntimeDispatchesAttackAndUpdatesCombatState() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        Monster target = mockMob(new Point(140, 200), 9300505);
        AgentAttackPlan plan = new AgentAttackPlan(
                0, 0, 1, new Rectangle(100, 150, 80, 70),
                List.of(target), AgentAttackRoute.CLOSE,
                4, 1, 1, AgentAttackExecutionProvider.attackPacketStance(true),
                4, 300, 600, WeaponType.SWORD1H);

        try (MockedStatic<AgentAttackExecutionProvider> attacks =
                     Mockito.mockStatic(AgentAttackExecutionProvider.class, Mockito.CALLS_REAL_METHODS)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.SWORD1H);
            attacks.when(() -> AgentAttackExecutionProvider.applyAttackRoute(
                    any(AgentAttackRoute.class), any(AbstractDealDamageHandler.AttackInfo.class), eq(bot)))
                    .thenAnswer(invocation -> null);

            runWithStubbedBotAfter(() -> AgentCombatAttackRuntime.attackMonster(entry, bot, plan));

            attacks.verify(() -> AgentAttackExecutionProvider.applyAttackRoute(
                    eq(AgentAttackRoute.CLOSE), any(AbstractDealDamageHandler.AttackInfo.class), eq(bot)));
        }

        assertEquals(600, AgentCombatCooldownStateRuntime.attackCooldownMs(entry));
        assertEquals(-1, AgentMovementStateRuntime.facingDirection(entry));
        assertTrue(AgentCombatCooldownStateRuntime.alertedUntilMs(entry) > System.currentTimeMillis());
    }

    @Test
    void basicAttackPlanRuntimeBuildsBasicAttackPlan() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Monster target = mockMob(new Point(140, 200), 9300506);
        when(map.getAllMonsters()).thenReturn(List.of(target));

        try (MockedStatic<AgentAttackExecutionProvider> attacks =
                     Mockito.mockStatic(AgentAttackExecutionProvider.class, Mockito.CALLS_REAL_METHODS)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.SWORD1H);

            AgentAttackPlan plan = AgentBasicAttackPlanRuntime.planBasicAttack(bot, target);

            assertNotNull(plan);
            assertEquals(0, plan.skillId);
            assertEquals(1, plan.numDamage);
            assertEquals(List.of(target), plan.targets);
            assertEquals(AgentAttackRoute.CLOSE, plan.route);
            assertNotNull(plan.hitBox);
        }
    }

    @Test
    void skillAttackPlanRuntimeBuildsSkillAttackPlan() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Monster target = mockMob(new Point(140, 200), 9300507);
        when(map.getAllMonsters()).thenReturn(List.of(target));

        Skill powerStrike = skillWithAttackBox(Warrior.POWER_STRIKE, 1, 1, 180,
                new Rectangle(90, 150, 120, 80));
        when(bot.getSkillLevel(powerStrike)).thenReturn((byte) 1);

        try (MockedStatic<SkillFactory> skillFactory = Mockito.mockStatic(SkillFactory.class);
             MockedStatic<AgentAttackExecutionProvider> attacks =
                     Mockito.mockStatic(AgentAttackExecutionProvider.class, Mockito.CALLS_REAL_METHODS)) {
            skillFactory.when(() -> SkillFactory.getSkill(Warrior.POWER_STRIKE)).thenReturn(powerStrike);
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.SWORD1H);

            AgentAttackPlan plan = AgentSkillAttackPlanRuntime.planSkillAttack(
                    bot, target, Warrior.POWER_STRIKE, AgentCombatConfig.cfg);

            assertNotNull(plan);
            assertEquals(Warrior.POWER_STRIKE, plan.skillId);
            assertEquals(1, plan.skillLevel);
            assertEquals(List.of(target), plan.targets);
            assertEquals(AgentAttackRoute.CLOSE, plan.route);
            assertNotNull(plan.hitBox);
        }
    }

    @Test
    void combatPlanRuntimeSelectsBasicAttackWhenNoSkillIsCached() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Monster target = mockMob(new Point(140, 200), 9300508);
        when(map.getAllMonsters()).thenReturn(List.of(target));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        try (MockedStatic<AgentAttackExecutionProvider> attacks =
                     Mockito.mockStatic(AgentAttackExecutionProvider.class, Mockito.CALLS_REAL_METHODS)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.SWORD1H);

            AgentAttackPlan plan = AgentCombatPlanRuntime.planAttack(entry, bot, target, AgentCombatConfig.cfg);

            assertNotNull(plan);
            assertEquals(0, plan.skillId);
            assertEquals(List.of(target), plan.targets);
        }
    }

    @Test
    void combatGroundRuntimeReturnsNullWithoutMapContext() {
        Character bot = mock(Character.class);
        when(bot.getMap()).thenReturn(null);

        assertNull(AgentCombatGroundRuntime.findGroundFoothold(new Point(100, 200), bot));
        assertNull(AgentCombatGroundRuntime.findGroundFoothold(null, bot));
        assertNull(AgentCombatGroundRuntime.findGroundFoothold(new Point(100, 200), null));
    }

    @Test
    void aoeRepositionRuntimeReturnsNullWhenDisabled() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Monster target = mockMob(new Point(140, 200), 9300509);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentAttackPlan fireNow = new AgentAttackPlan(
                0, 0, 1, new Rectangle(100, 150, 80, 70),
                List.of(target), AgentAttackRoute.CLOSE,
                4, 1, 1, 0, 4, 300, 600, null);
        boolean original = AgentCombatConfig.cfg.AOE_REPOSITION_ENABLED;
        AgentCombatConfig.cfg.AOE_REPOSITION_ENABLED = false;
        try {
            assertNull(AgentCombatAoeRepositionRuntime.aoeRepositionTarget(
                    entry, bot, target, fireNow, AgentCombatConfig.cfg));
        } finally {
            AgentCombatConfig.cfg.AOE_REPOSITION_ENABLED = original;
        }
    }

    @Test
    void combatTargetRuntimeReturnsNullWhenNoCandidatesExist() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        when(map.getAllMonsters()).thenReturn(List.of());

        assertNull(AgentCombatTargetRuntime.findGrindTarget(
                new AgentRuntimeEntry(bot, null, null), bot, AgentCombatConfig.cfg));
    }

    @Test
    void combatDeathRuntimeEntersDeadWindowWithoutAnnouncement() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        AgentCombatDeathRuntime.enterDeadState(entry, bot, false, AgentCombatConfig.cfg);

        assertTrue(AgentDeathStateRuntime.deadUntilMs(entry) > System.currentTimeMillis());
    }

    @Test
    void shouldMatchOpenStoryGroundMobKnockbackWhenHitFromRight() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Monster mob = mockMob(new Point(140, 200), 9300000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setFacingDirection(entry, 1);

        runWithStubbedBotAfter(() -> AgentCombatDamageRuntime.applyMobHit(entry, bot, mob, AgentCombatConfig.cfg));

        assertTrue(AgentMovementStateRuntime.inAir(entry));
        assertFalse(AgentClimbStateRuntime.climbing(entry));
        assertTrue(AgentClimbStateRuntime.climbUpIntent(entry));
        assertEquals(new Point(100, 200), bot.getPosition());
        assertEquals(-Math.round(1.5f * AgentMovementPhysicsConfig.configuredMovementTickMs() / 8.0f), AgentMovementPhysicsStateRuntime.airVelocityX(entry));
        assertEquals(-3.5f * AgentMovementPhysicsConfig.configuredMovementTickMs() / 8.0f, AgentMovementPhysicsStateRuntime.verticalVelocity(entry), 1.0e-4f);
        assertEquals(1, AgentMovementStateRuntime.facingDirection(entry));
        assertEquals(CharacterStance.JUMP_RIGHT_STANCE, bot.getStance());
        assertDamageDirection(map, bot, 2, 0);
    }

    @Test
    void shouldOnlyRedirectHorizontalVelocityWhenMobHitOccursMidAir() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Monster mob = mockMob(new Point(60, 200), 9300001);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setInAir(entry, true);
        AgentMovementPhysicsStateRuntime.setPhysicsX(entry, 100);
        AgentMovementPhysicsStateRuntime.setPhysicsY(entry, 200);
        AgentMovementPhysicsStateRuntime.setVerticalVelocity(entry, 12.5f);
        AgentMovementPhysicsStateRuntime.setAirVelocityX(entry, -4);
        AgentMovementStateRuntime.setFacingDirection(entry, -1);

        runWithStubbedBotAfter(() -> AgentCombatDamageRuntime.applyMobHit(entry, bot, mob, AgentCombatConfig.cfg));

        assertTrue(AgentMovementStateRuntime.inAir(entry));
        assertTrue(AgentClimbStateRuntime.climbUpIntent(entry));
        assertEquals(new Point(100, 200), bot.getPosition());
        assertEquals(12.5f, AgentMovementPhysicsStateRuntime.verticalVelocity(entry), 1.0e-4f);
        assertEquals(Math.round(1.5f * AgentMovementPhysicsConfig.configuredMovementTickMs() / 8.0f), AgentMovementPhysicsStateRuntime.airVelocityX(entry));
        assertEquals(-1, AgentMovementStateRuntime.facingDirection(entry));
        assertEquals(CharacterStance.JUMP_LEFT_STANCE, bot.getStance());
        assertDamageDirection(map, bot, 2, 1);
    }

    @Test
    void shouldSkipMobKnockbackWhenStanceBuffIsMaxed() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, 100);
        Monster mob = mockMob(new Point(140, 200), 9300002);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        runWithStubbedBotAfter(() -> AgentCombatDamageRuntime.applyMobHit(entry, bot, mob, AgentCombatConfig.cfg));

        assertFalse(AgentMovementStateRuntime.inAir(entry));
        assertFalse(AgentClimbStateRuntime.climbing(entry));
        assertEquals(new Point(100, 200), bot.getPosition());
        assertEquals(0, AgentMovementPhysicsStateRuntime.airVelocityX(entry));
        assertEquals(0.0f, AgentMovementPhysicsStateRuntime.verticalVelocity(entry), 1.0e-4f);
        assertDamageDirection(map, bot, 1, 0);
    }

    @Test
    void shouldKeepDirectionAwareDeadStanceAfterFatalMobHit() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 1, null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setFacingDirection(entry, -1);
        Monster mob = mockMob(new Point(140, 200), 9300003);

        runWithStubbedBotAfter(() -> AgentCombatDamageRuntime.applyMobHit(entry, bot, mob, AgentCombatConfig.cfg));

        assertEquals(CharacterStance.DEAD_LEFT_STANCE, bot.getStance());
        assertTrue(AgentDeathStateRuntime.deadUntilMs(entry) > 0);
        assertFalse(AgentMovementStateRuntime.inAir(entry));
        assertFalse(AgentClimbStateRuntime.climbing(entry));
    }

    @Test
    void shouldNotTargetFriendlyMonsterWhenSearchingForClosestTarget() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Monster friendly = mockFriendlyMob(new Point(140, 200), 9300500);
        Monster hostile = mockMob(new Point(160, 200), 9300501);

        // Only a friendly mob on the map -> no attackable target, even though it is in range.
        when(map.getAllMonsters()).thenReturn(List.of(friendly));
        assertNull(AgentCombatTargetSelector.findClosestAliveMonster(
                map.getAllMonsters(), bot.getPosition(), 1_000_000d));

        // Friendly mob is closer, but the bot must skip it and pick the farther hostile mob.
        when(map.getAllMonsters()).thenReturn(List.of(friendly, hostile));
        assertEquals(hostile, AgentCombatTargetSelector.findClosestAliveMonster(
                map.getAllMonsters(), bot.getPosition(), 1_000_000d));
    }

    @Test
    void shouldNotTakeContactDamageFromFriendlyMonster() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Monster friendly = mockFriendlyMob(new Point(100, 200), 9300502);
        when(map.getAllMonsters()).thenReturn(List.of(friendly));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        try (MockedStatic<AgentMobTouchRuntime> combat =
                     Mockito.mockStatic(AgentMobTouchRuntime.class, Mockito.CALLS_REAL_METHODS)) {
            combat.when(() -> AgentMobTouchRuntime.isMobTouchingAgent(any(AgentRuntimeEntry.class), any(Character.class),
                    any(Monster.class), anyInt())).thenReturn(true);
            runWithStubbedBotAfter(() -> AgentCombatDamageRuntime.tickMobDamage(
                    entry, bot, AgentCombatConfig.cfg, AgentMovementTimers::tickDown));
        }

        assertEquals(20_000, bot.getHp());
        verify(map, never()).broadcastMessage(any(Character.class), any(Packet.class), anyBoolean());
    }

    @Test
    void shouldTakeContactDamageFromHostileMonster() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Monster hostile = mockMob(new Point(100, 200), 9300503);
        when(map.getAllMonsters()).thenReturn(List.of(hostile));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        try (MockedStatic<AgentMobTouchRuntime> combat =
                     Mockito.mockStatic(AgentMobTouchRuntime.class, Mockito.CALLS_REAL_METHODS)) {
            combat.when(() -> AgentMobTouchRuntime.isMobTouchingAgent(any(AgentRuntimeEntry.class), any(Character.class),
                    any(Monster.class), anyInt())).thenReturn(true);
            runWithStubbedBotAfter(() -> AgentCombatDamageRuntime.tickMobDamage(
                    entry, bot, AgentCombatConfig.cfg, AgentMovementTimers::tickDown));
        }

        assertTrue(bot.getHp() < 20_000, "hostile contact should reduce bot HP");
    }

    @Test
    void shouldFallbackToBasicAttackTimingWhenSkillAnimationDelayMissing() {
        AgentAttackExecutionProvider.SkillAttackTiming timing =
                AgentAttackExecutionProvider.resolveSkillAttackTiming(0, 4, 300, 590);

        assertEquals(300, timing.hitDelayMs());
        assertEquals(590, timing.cooldownMs());
    }

    @Test
    void shouldNotUnlockSkillFasterThanBasicAttackCooldown() {
        AgentAttackExecutionProvider.SkillAttackTiming timing =
                AgentAttackExecutionProvider.resolveSkillAttackTiming(450, 4, 300, 590);

        assertEquals(197, timing.hitDelayMs());
        assertEquals(590, timing.cooldownMs());
    }

    @Test
    void shouldApplyAttackSpeedScalingToSkillAnimationTiming() {
        AgentAttackExecutionProvider.SkillAttackTiming timing =
                AgentAttackExecutionProvider.resolveSkillAttackTiming(520, 2, 120, 0);

        assertEquals(203, timing.hitDelayMs());
        assertEquals(406, timing.cooldownMs());
    }

    @Test
    void shouldUseDegenerateCloseAttackPoolForBowAtPointBlankRange() {
        assertTrue(AgentAttackExecutionProvider.shouldDegenerateRangedAttack(WeaponType.BOW, new Point(100, 200), new Point(145, 200)));
        AgentAttackDataProvider.AttackAnimationSpec bowSpec = AgentAttackDataProvider.getInstance().getBasicAttackSpec(WeaponType.BOW, true);

        assertEquals(3, bowSpec.display());
        assertTrue(bowSpec.actions().contains("swingT1"));
        assertTrue(bowSpec.actions().contains("swingT3"));
    }

    @Test
    void shouldKeepBowOutOfDegenerateModeWhenTargetIsNotCrowding() {
        assertFalse(AgentAttackExecutionProvider.shouldDegenerateRangedAttack(WeaponType.BOW, new Point(100, 200), new Point(300, 200)));
        AgentAttackDataProvider.AttackAnimationSpec bowSpec = AgentAttackDataProvider.getInstance().getBasicAttackSpec(WeaponType.BOW, false);

        assertEquals("shoot1", bowSpec.primaryAction());
    }

    @Test
    void shouldRetreatFromNearbyRangedTargetsInsideRetreatBand() {
        Point botPos = new Point(100, 200);
        Point retreatBandTarget = new Point(botPos.x + AgentCombatConfig.cfg.RANGED_RETREAT_THRESHOLD_X, 200);
        Point pointBlankTarget = new Point(botPos.x + 1, 200);
        Point justOutsideRetreatTarget = new Point(botPos.x + AgentCombatConfig.cfg.RANGED_RETREAT_THRESHOLD_X + 1, 200);
        Point farTarget = new Point(botPos.x + AgentCombatConfig.cfg.RANGED_DEGENERATE_RANGE_X + 100, 200);

        assertTrue(AgentAttackExecutionProvider.shouldRetreatFromNearbyTarget(WeaponType.BOW, botPos, retreatBandTarget));
        assertEquals(new Point(botPos.x - AgentCombatConfig.cfg.RANGED_RETREAT_DISTANCE_X, 200),
                AgentAttackExecutionProvider.retreatTargetPosition(botPos, retreatBandTarget));
        assertTrue(AgentAttackExecutionProvider.shouldRetreatFromNearbyTarget(WeaponType.BOW, botPos, pointBlankTarget));
        assertFalse(AgentAttackExecutionProvider.shouldRetreatFromNearbyTarget(WeaponType.BOW, botPos, justOutsideRetreatTarget));
        assertFalse(AgentAttackExecutionProvider.shouldRetreatFromNearbyTarget(WeaponType.BOW, botPos, farTarget));
    }

    @Test
    void shouldAllowDiagonalJumpAttackForCloseRangeTargetsSlightlyAbove() {
        assertTrue(AgentCombatRangePolicy.isTargetJumpable(
                AgentMovementProfile.base(), true, new Point(100, 200), new Point(230, 135),
                AgentMovementKinematicsService.calculateMaxJumpHeight(AgentMovementProfile.base())));
    }

    @Test
    void shouldRejectJumpAttackForNonCloseRangeRoutes() {
        assertFalse(AgentCombatRangePolicy.isTargetJumpable(
                AgentMovementProfile.base(), false, new Point(100, 200), new Point(170, 135),
                AgentMovementKinematicsService.calculateMaxJumpHeight(AgentMovementProfile.base())));
    }

    @Test
    void shouldRejectAirborneRangedAttackPlansForWeaponsThatCannotJumpShoot() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setInAir(entry, true);
        AgentAttackPlan rangedBowPlan = new AgentAttackPlan(
                Hunter.ARROW_BOMB, 1, 1, new Rectangle(100, 150, 300, 100),
                List.of(mockMob(new Point(180, 200), 9300200)), AgentAttackRoute.RANGED,
                0, 11, 11, 11, 4, 300, 600, null);
        AgentAttackPlan closePlan = new AgentAttackPlan(
                0, 0, 1, new Rectangle(100, 150, 80, 70),
                List.of(mockMob(new Point(120, 200), 9300201)), AgentAttackRoute.CLOSE,
                4, 1, 1, 0, 4, 300, 600, null);

        assertFalse(AgentCombatRangePolicy.canUseAttackPlanNow(false, WeaponType.BOW, rangedBowPlan.route));
        assertFalse(AgentCombatRangePolicy.canUseAttackPlanNow(false, WeaponType.CROSSBOW, rangedBowPlan.route));
        assertFalse(AgentCombatRangePolicy.canUseAttackPlanNow(false, WeaponType.GUN, rangedBowPlan.route));
        assertTrue(AgentCombatRangePolicy.canUseAttackPlanNow(false, WeaponType.CLAW, rangedBowPlan.route));
        assertTrue(AgentCombatRangePolicy.canUseAttackPlanNow(false, WeaponType.BOW, closePlan.route));
    }

    @Test
    void shouldRememberLeftFacingAttackForNextStandingStance() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setFacingDirection(entry, 1);

        AgentCombatFacingRuntime.rememberAttackFacing(entry, AgentAttackExecutionProvider.attackPacketStance(true));

        assertEquals(-1, AgentMovementStateRuntime.facingDirection(entry));
        assertEquals(CharacterStance.STAND_LEFT_STANCE, bot.getStance());
    }

    @Test
    void shouldRememberRightFacingAttackForNextStandingStance() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMovementStateRuntime.setFacingDirection(entry, -1);

        AgentCombatFacingRuntime.rememberAttackFacing(entry, AgentAttackExecutionProvider.attackPacketStance(false));

        assertEquals(1, AgentMovementStateRuntime.facingDirection(entry));
        assertEquals(CharacterStance.STAND_RIGHT_STANCE, bot.getStance());
    }

    @Test
    void shouldAnchorArrowBombOnClosestMobInProjectilePath() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Monster closest = mockMob(new Point(260, 200), 9300300);
        Monster splash = mockMob(new Point(275, 200), 9300301);
        Monster farSelected = mockMob(new Point(390, 200), 9300302);
        doReturn(List.of(farSelected, splash, closest)).when(map).getAllMonsters();

        Skill arrowBomb = skillWithAnchoredAoe(Hunter.ARROW_BOMB, 1, 6, 260);
        when(bot.getSkillLevel(arrowBomb)).thenReturn((byte) 1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheStateRuntime.setAoeSkill(entry, Hunter.ARROW_BOMB, AgentCombatSkillCacheStateRuntime.aoeSkillMobs(entry));

        try (MockedStatic<SkillFactory> skillFactory = Mockito.mockStatic(SkillFactory.class);
             MockedStatic<AgentAttackExecutionProvider> attackExecution =
                     Mockito.mockStatic(AgentAttackExecutionProvider.class, Mockito.CALLS_REAL_METHODS)) {
            skillFactory.when(() -> SkillFactory.getSkill(Hunter.ARROW_BOMB)).thenReturn(arrowBomb);
            attackExecution.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.BOW);

            AgentAttackPlan plan = AgentCombatPlanRuntime.planAttack(entry, bot, farSelected, AgentCombatConfig.cfg);

            assertEquals(Hunter.ARROW_BOMB, plan.skillId);
            assertEquals(closest, plan.targets.get(0));
            assertTrue(plan.targets.contains(splash));
            assertFalse(plan.targets.contains(farSelected));
        }
    }

    @Test
    void shouldRejectJumpAttackWhenTargetIsTooHighOrTooFar() {
        assertFalse(AgentCombatRangePolicy.isTargetJumpable(
                AgentMovementProfile.base(), true, new Point(100, 200), new Point(241, 135),
                AgentMovementKinematicsService.calculateMaxJumpHeight(AgentMovementProfile.base())));
        assertFalse(AgentCombatRangePolicy.isTargetJumpable(
                AgentMovementProfile.base(), true, new Point(100, 200), new Point(170, 60),
                AgentMovementKinematicsService.calculateMaxJumpHeight(AgentMovementProfile.base())));
    }

    @Test
    void shouldUseClientStyleMobTouchSweepForStationaryBot() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        Rectangle bounds = AgentMobTouchRuntime.agentTouchBounds(entry, bot, AgentCombatConfig.cfg.MOB_TOUCH_SWEEP_HEIGHT);

        assertEquals(new Rectangle(100, 150, 1, 51), bounds);
    }

    @Test
    void shouldIgnoreMobThatOnlyBrushesBotSpriteSide() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        Monster mob = mockMob(new Point(122, 200), 100100);
        when(mob.isFacingLeft()).thenReturn(false);

        assertFalse(AgentMobTouchRuntime.isMobTouchingAgent(entry, bot, mob, AgentCombatConfig.cfg.MOB_TOUCH_SWEEP_HEIGHT));
    }

    @Test
    void shouldDetectMobTouchAcrossBotMovementSweep() {
        Character bot = mockBot(new Point(120, 200), mock(MapleMap.class), 20_000, null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentMobTouchStateRuntime.rememberCheck(entry, new Point(80, 200), 0);
        when(bot.getMapId()).thenReturn(0);
        Monster mob = mockMob(new Point(96, 200), 100100);
        when(mob.isFacingLeft()).thenReturn(false);

        assertTrue(AgentMobTouchRuntime.isMobTouchingAgent(entry, bot, mob, AgentCombatConfig.cfg.MOB_TOUCH_SWEEP_HEIGHT));
    }

    @Test
    void shouldAllowPathScoringToBeatFarCurrentFootholdTarget() {
        MapleMap map = spy(new MapleMap(910009050, 0, 0, 910009050, 1.0f));
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(300, 100), 1));
        footholds.insert(new Foothold(new Point(0, 200), new Point(300, 200), 2));
        map.setFootholds(footholds);
        AgentNavigationGraphService.rebuildGraph(map);

        Character bot = mockBot(new Point(100, 100), map, 20_000, null);
        Monster currentFootholdMob = mockMob(new Point(180, 100), 100100);
        Monster otherRegionMob = mockMob(new Point(105, 200), 100100);
        doReturn(List.of(otherRegionMob, currentFootholdMob)).when(map).getAllMonsters();

        Monster target = AgentCombatTargetRuntime.findGrindTarget(
                new AgentRuntimeEntry(bot, null, null), bot, AgentCombatConfig.cfg);

        assertEquals(otherRegionMob, target);
    }

    @Test
    void shouldPreferAoeClusterAnchorOverLoneCloseMobWhenBotHasAoeSkill() {
        // bot at (100, 100). lone "low-hp" mob CLOSE in one direction, 3-mob cluster
        // farther in the opposite direction. without an AoE skill the close lone mob
        // wins on distance score; with an AoE skill, the cluster anchor must win so
        // planAttack can fire an AoE plan that out-DPSes the basic single shot.
        MapleMap map = spy(new MapleMap(910009060, 0, 0, 910009060, 1.0f));
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(-400, 100), new Point(400, 100), 1));
        map.setFootholds(footholds);
        AgentNavigationGraphService.rebuildGraph(map);

        Character bot = mockBot(new Point(100, 100), map, 20_000, null);
        Monster loneClose = mockMob(new Point(160, 100), 100100);
        Monster clusterAnchor = mockMob(new Point(-100, 100), 100100);
        Monster clusterNeighbor1 = mockMob(new Point(-130, 100), 100100);
        Monster clusterNeighbor2 = mockMob(new Point(-160, 100), 100100);
        doReturn(List.of(loneClose, clusterAnchor, clusterNeighbor1, clusterNeighbor2))
                .when(map).getAllMonsters();

        AgentRuntimeEntry noAoeEntry = new AgentRuntimeEntry(bot, null, null);
        // Sanity: without AoE skill, the lone close mob wins on plain distance score.
        assertEquals(loneClose, AgentCombatTargetRuntime.findGrindTarget(noAoeEntry, bot, AgentCombatConfig.cfg));

        AgentRuntimeEntry aoeEntry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheStateRuntime.setAoeSkill(aoeEntry, Hunter.POWER_KNOCKBACK, 6);
        // With AoE skill that hits up to 6, cluster anchor's bonus overcomes the lone
        // mob's distance advantage and the bot switches target to the cluster.
        assertEquals(clusterAnchor, AgentCombatTargetRuntime.findGrindTarget(aoeEntry, bot, AgentCombatConfig.cfg));
    }

    @Test
    void shouldPreferLessOccupiedGrindRegionWhenPathCostIsClose() throws Exception {
        MapleMap map = spy(new MapleMap(910009052, 0, 0, 910009052, 1.0f));
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        Foothold startFoothold = new Foothold(new Point(0, 100), new Point(100, 100), 1);
        Foothold occupiedFoothold = new Foothold(new Point(200, 100), new Point(300, 100), 2);
        Foothold openFoothold = new Foothold(new Point(320, 100), new Point(420, 100), 3);
        footholds.insert(startFoothold);
        footholds.insert(occupiedFoothold);
        footholds.insert(openFoothold);
        map.setFootholds(footholds);

        AgentNavigationGraph.Region startRegion = new AgentNavigationGraph.Region(
                1, List.of(new AgentNavigationGraph.Segment(startFoothold)));
        AgentNavigationGraph.Region occupiedRegion = new AgentNavigationGraph.Region(
                2, List.of(new AgentNavigationGraph.Segment(occupiedFoothold)));
        AgentNavigationGraph.Region openRegion = new AgentNavigationGraph.Region(
                3, List.of(new AgentNavigationGraph.Segment(openFoothold)));
        AgentNavigationGraph graph = new AgentNavigationGraph(
                map.getId(),
                1,
                AgentMovementProfile.base(),
                List.of(startRegion, occupiedRegion, openRegion),
                Map.of(1, startRegion, 2, occupiedRegion, 3, openRegion),
                Map.of(1, 1, 2, 2, 3, 3),
                Map.of(1, List.of(
                        new AgentNavigationGraph.Edge(1, 2, AgentNavigationGraph.EdgeType.WALK,
                                new Point(100, 100), new Point(200, 100), 0, 0, 0, 0, 0, 100),
                        new AgentNavigationGraph.Edge(1, 3, AgentNavigationGraph.EdgeType.WALK,
                                new Point(100, 100), new Point(320, 100), 0, 0, 0, 0, 0, 150))),
                Set.of());

        Character owner = mock(Character.class);
        when(owner.getId()).thenReturn(909052);
        Character bot = mockBot(new Point(50, 100), map, 20_000, null);
        Character siblingBot = mockBot(new Point(220, 100), map, 20_000, null);
        Monster occupiedTarget = mockMob(new Point(220, 100), 9300400);
        Monster openTarget = mockMob(new Point(340, 100), 9300401);
        doReturn(List.of(occupiedTarget, openTarget)).when(map).getAllMonsters();

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);
        AgentModeStateRuntime.setGrinding(entry, true);
        AgentRuntimeEntry siblingEntry = new AgentRuntimeEntry(siblingBot, owner, null);
        AgentModeStateRuntime.setGrinding(siblingEntry, true);

        Map<Integer, List<AgentRuntimeEntry>> bots = AgentRuntimeRegistry.entriesByLeaderId();
        bots.put(owner.getId(), new CopyOnWriteArrayList<>(List.of(entry, siblingEntry)));
        try (MockedStatic<AgentNavigationGraphService> graphProvider =
                     Mockito.mockStatic(AgentNavigationGraphService.class, Mockito.CALLS_REAL_METHODS)) {
            graphProvider.when(() -> AgentNavigationGraphService.peekGraph(map, AgentMovementProfile.base()))
                    .thenReturn(graph);

            Monster target = AgentCombatTargetRuntime.findGrindTarget(entry, bot, AgentCombatConfig.cfg);

            assertEquals(openTarget, target);
        } finally {
            bots.remove(owner.getId());
        }
    }

    @Test
    void shouldUseRangedHitBoxTargetOutsideCurrentRegionWithoutPathingThere() {
        MapleMap map = spy(new MapleMap(910009051, 0, 0, 910009051, 1.0f));
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(200, 100), 1));
        footholds.insert(new Foothold(new Point(250, 130), new Point(450, 130), 2));
        map.setFootholds(footholds);
        AgentNavigationGraphService.rebuildGraph(map);

        Character bot = mockBot(new Point(100, 100), map, 20_000, null);
        Monster otherRegionMob = mockMob(new Point(300, 130), 100100);
        doReturn(List.of(otherRegionMob)).when(map).getAllMonsters();

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        try (MockedStatic<AgentAttackExecutionProvider> attackExecution =
                     Mockito.mockStatic(AgentAttackExecutionProvider.class, Mockito.CALLS_REAL_METHODS)) {
            attackExecution.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.BOW);

            assertEquals(otherRegionMob, AgentCombatTargetRuntime.findFollowAttackTarget(entry, bot, AgentCombatConfig.cfg));
            assertEquals(otherRegionMob, AgentCombatTargetRuntime.findGrindTarget(entry, bot, AgentCombatConfig.cfg));
            assertTrue(AgentCombatTargetRuntime.isReachableGrindTarget(entry, bot, otherRegionMob));
        }
    }

    @Test
    void shouldExcludeOneWayPatrolNeighborFromRoamTargeting() {
        MapleMap map = spy(new MapleMap(910009053, 0, 0, 910009053, 1.0f));
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        Foothold homeFoothold = new Foothold(new Point(0, 100), new Point(100, 100), 1);
        Foothold oneWayFoothold = new Foothold(new Point(200, 140), new Point(300, 140), 2);
        Foothold returnableFoothold = new Foothold(new Point(400, 100), new Point(500, 100), 3);
        footholds.insert(homeFoothold);
        footholds.insert(oneWayFoothold);
        footholds.insert(returnableFoothold);
        map.setFootholds(footholds);

        AgentNavigationGraph.Region homeRegion = new AgentNavigationGraph.Region(
                1, List.of(new AgentNavigationGraph.Segment(homeFoothold)));
        AgentNavigationGraph.Region oneWayRegion = new AgentNavigationGraph.Region(
                2, List.of(new AgentNavigationGraph.Segment(oneWayFoothold)));
        AgentNavigationGraph.Region returnableRegion = new AgentNavigationGraph.Region(
                3, List.of(new AgentNavigationGraph.Segment(returnableFoothold)));
        AgentNavigationGraph graph = new AgentNavigationGraph(
                map.getId(),
                1,
                AgentMovementProfile.base(),
                List.of(homeRegion, oneWayRegion, returnableRegion),
                Map.of(1, homeRegion, 2, oneWayRegion, 3, returnableRegion),
                Map.of(1, 1, 2, 2, 3, 3),
                Map.of(
                        1, List.of(
                                new AgentNavigationGraph.Edge(1, 2, AgentNavigationGraph.EdgeType.DROP,
                                        new Point(100, 100), new Point(200, 140), 0, 0, 0, 0, 0, 100),
                                new AgentNavigationGraph.Edge(1, 3, AgentNavigationGraph.EdgeType.WALK,
                                        new Point(100, 100), new Point(400, 100), 0, 0, 0, 0, 0, 120)),
                        3, List.of(new AgentNavigationGraph.Edge(3, 1, AgentNavigationGraph.EdgeType.WALK,
                                new Point(400, 100), new Point(100, 100), 0, 0, 0, 0, 0, 120))),
                Set.of());

        Character bot = mockBot(new Point(50, 100), map, 20_000, null);
        Monster oneWayTarget = mockMob(new Point(240, 140), 9300402);
        Monster returnableTarget = mockMob(new Point(440, 100), 9300403);
        doReturn(List.of(oneWayTarget, returnableTarget)).when(map).getAllMonsters();

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentPatrolStateRuntime.startPatrol(entry, 1, map.getId());

        try (MockedStatic<AgentNavigationGraphService> graphProvider =
                     Mockito.mockStatic(AgentNavigationGraphService.class, Mockito.CALLS_REAL_METHODS)) {
            graphProvider.when(() -> AgentNavigationGraphService.peekGraph(map, AgentMovementProfile.base()))
                    .thenReturn(graph);

            Monster target = AgentCombatTargetRuntime.findPatrolTarget(entry, bot, AgentCombatConfig.cfg);

            assertEquals(returnableTarget, target);
        }
    }

    private static void assertDamageDirection(MapleMap map, Character bot, int expectedBroadcasts, int expectedDirection) {
        ArgumentCaptor<Packet> packets = ArgumentCaptor.forClass(Packet.class);
        verify(map, times(expectedBroadcasts)).broadcastMessage(eq(bot), packets.capture(), eq(false));
        byte[] payload = packets.getAllValues().get(0).getBytes();
        assertEquals(expectedDirection, Byte.toUnsignedInt(payload[15]));
    }

    private static void runWithStubbedBotAfter(Runnable action) {
        try (MockedStatic<AgentSchedulerRuntime> scheduler =
                     Mockito.mockStatic(AgentSchedulerRuntime.class, Mockito.CALLS_REAL_METHODS)) {
            scheduler.when(() -> AgentSchedulerRuntime.afterDelay(anyLong(), any(Runnable.class)))
                    .thenAnswer(invocation -> null);
            action.run();
        }
    }

    private static Character mockBot(Point startPosition, MapleMap map, int startingHp, Integer stancePercent) {
        Character bot = mock(Character.class);
        Inventory equipped = mock(Inventory.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(startPosition));
        AtomicInteger hp = new AtomicInteger(startingHp);
        AtomicInteger stance = new AtomicInteger(CharacterStance.STAND_RIGHT_STANCE);

        when(bot.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(bot).setPosition(any(Point.class));
        when(bot.getMap()).thenReturn(map);
        when(bot.getHp()).thenAnswer(invocation -> hp.get());
        when(bot.getCurrentMaxHp()).thenReturn(startingHp);
        doAnswer(invocation -> {
            hp.addAndGet(invocation.getArgument(0));
            return null;
        }).when(bot).addMPHPAndTriggerAutopot(anyInt(), anyInt());
        when(bot.getStance()).thenAnswer(invocation -> stance.get());
        doAnswer(invocation -> {
            stance.set(invocation.getArgument(0));
            return null;
        }).when(bot).setStance(anyInt());
        when(bot.getId()).thenReturn(1);
        when(bot.getMapId()).thenReturn(0);
        when(bot.getJob()).thenReturn(Job.BEGINNER);
        when(bot.getLevel()).thenReturn(200);
        when(bot.isAlive()).thenReturn(true);
        when(bot.isFacingLeft()).thenReturn(false);
        when(bot.getTotalMoveSpeedStat()).thenReturn(100);
        when(bot.getTotalJumpStat()).thenReturn(100);
        when(bot.getTotalWdef()).thenReturn(0);
        when(bot.getTotalStr()).thenReturn(4);
        when(bot.getTotalDex()).thenReturn(4);
        when(bot.getTotalInt()).thenReturn(4);
        when(bot.getTotalLuk()).thenReturn(4);
        when(bot.getTotalWatk()).thenReturn(100);
        when(bot.getEnergyBar()).thenReturn(0);
        when(bot.getAllBuffs()).thenReturn(Collections.emptyList());
        when(bot.calculateMaxBaseDamage(anyInt())).thenReturn(1_000);
        when(bot.calculateMinBaseDamage(anyInt())).thenReturn(500);
        when(bot.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(equipped.getItem((short) -11)).thenReturn(null);
        when(equipped.iterator()).thenReturn(Collections.emptyIterator());
        if (stancePercent != null) {
            when(bot.getBuffedValue(BuffStat.STANCE)).thenReturn(stancePercent);
        }
        return bot;
    }

    private static Monster mockMob(Point position, int id) {
        Monster mob = mock(Monster.class);
        when(mob.getPosition()).thenReturn(new Point(position));
        when(mob.getId()).thenReturn(id);
        when(mob.getObjectId()).thenReturn(id);
        when(mob.getPADamage()).thenReturn(1_000);
        when(mob.getLevel()).thenReturn(1);
        when(mob.getAccuracy()).thenReturn(9_999);
        when(mob.getAvoidability()).thenReturn(0);
        when(mob.getWdef()).thenReturn(0);
        when(mob.getMdef()).thenReturn(0);
        when(mob.getHp()).thenReturn(10_000);
        when(mob.getMaxHp()).thenReturn(10_000);
        when(mob.isAlive()).thenReturn(true);
        return mob;
    }

    private static Monster mockFriendlyMob(Point position, int id) {
        Monster mob = mockMob(position, id);
        MonsterStats stats = mock(MonsterStats.class);
        when(stats.isFriendly()).thenReturn(true);
        when(mob.getStats()).thenReturn(stats);
        return mob;
    }

    private static Skill skillWithAttack(int skillId, int attackCount, int mobCount, int damage) {
        Skill skill = new Skill(skillId);
        StatEffect effect = mock(StatEffect.class);
        when(effect.getAttackCount()).thenReturn(attackCount);
        when(effect.getMobCount()).thenReturn(mobCount);
        when(effect.getDamage()).thenReturn(damage);
        when(effect.getDamagePercent()).thenReturn(damage);
        when(effect.hasDamage()).thenReturn(true);
        when(effect.getDuration()).thenReturn(0);
        when(effect.getMpCon()).thenReturn((short) 1);
        when(effect.canPaySkillCost(any(Character.class))).thenReturn(true);
        skill.addLevelEffect(effect);
        return skill;
    }

    private static Skill skillWithAttackBox(int skillId, int attackCount, int mobCount, int damage, Rectangle hitBox) {
        Skill skill = skillWithAttack(skillId, attackCount, mobCount, damage);
        return skillWithAttackBox(skill, hitBox);
    }

    private static Skill skillWithAttackBox(int skillId, int attackCount, int mobCount, int damage,
                                            Rectangle hitBox, int animationTimeMs) {
        Skill skill = skillWithAttack(skillId, attackCount, mobCount, damage);
        skill.setAction0("testSkillDelay" + skillId);
        skill.setAnimationTime(animationTimeMs);
        return skillWithAttackBox(skill, hitBox);
    }

    private static Skill skillWithAttackBox(Skill skill, Rectangle hitBox) {
        StatEffect effect = skill.getEffect(1);
        when(effect.hasBoundingBox()).thenReturn(true);
        when(effect.calculateBoundingBox(any(Point.class), anyBoolean())).thenReturn(new Rectangle(hitBox));
        return skill;
    }

    private static Skill passiveOverTimeSkillWithCombatMetadata(int skillId, int damage, int attackCount, int mobCount) {
        Skill skill = new Skill(skillId);
        StatEffect effect = mock(StatEffect.class);
        when(effect.getDamage()).thenReturn(damage);
        when(effect.getAttackCount()).thenReturn(attackCount);
        when(effect.getBulletCount()).thenReturn((short) 0);
        when(effect.getMobCount()).thenReturn(mobCount);
        when(effect.isOverTime()).thenReturn(true);
        skill.addLevelEffect(effect);
        return skill;
    }

    private static Skill passiveSkillWithCombatMetadata(int skillId, int damage, int attackCount, int mobCount) {
        Skill skill = new Skill(skillId);
        StatEffect effect = mock(StatEffect.class);
        when(effect.getDamage()).thenReturn(damage);
        when(effect.getAttackCount()).thenReturn(attackCount);
        when(effect.getBulletCount()).thenReturn((short) 0);
        when(effect.getMobCount()).thenReturn(mobCount);
        when(effect.isOverTime()).thenReturn(false);
        skill.addLevelEffect(effect);
        return skill;
    }

    private static Skill skillWithBuffAction(int skillId) {
        Skill skill = new Skill(skillId);
        skill.setAction(true);
        StatEffect effect = mock(StatEffect.class);
        when(effect.isOverTime()).thenReturn(true);
        // Real support buffs are duration-based and grant the caster a statup; isActiveSupportSkill
        // now requires both, so the mock must mirror that.
        when(effect.getDuration()).thenReturn(900_000);
        when(effect.getStatups()).thenReturn(List.of(new tools.Pair<>(BuffStat.WDEF, 20)));
        skill.addLevelEffect(effect);
        return skill;
    }

    private static void assertRealWzCache(Job job, int level, Set<Integer> skillIds,
                                          int expectedAttackSkillId, int expectedAoeSkillId,
                                          Set<Integer> expectedBuffSkillIds, Set<Integer> excludedSkillIds) {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        when(bot.getJob()).thenReturn(job);
        when(bot.getLevel()).thenReturn(level);

        Map<Skill, Character.SkillEntry> skills = new LinkedHashMap<>();
        for (int skillId : skillIds) {
            Skill skill = SkillFactory.getSkill(skillId);
            assertTrue(skill != null, "missing real WZ skill " + skillId);
            skills.put(skill, null);
        }
        when(bot.getSkills()).thenReturn(skills);
        doAnswer(invocation -> {
            Skill skill = invocation.getArgument(0);
            return (byte) (skillIds.contains(skill.getId()) ? 1 : 0);
        }).when(bot).getSkillLevel(any(Skill.class));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);

        assertEquals(expectedAttackSkillId, AgentCombatSkillCacheStateRuntime.attackSkillId(entry));
        assertEquals(expectedAoeSkillId, AgentCombatSkillCacheStateRuntime.aoeSkillId(entry));
        for (int skillId : expectedBuffSkillIds) {
            assertTrue(AgentCombatSkillCacheStateRuntime.buffSkillIds(entry).contains(skillId), "expected cached buff " + skillId);
        }
        for (int skillId : excludedSkillIds) {
            assertFalse(AgentCombatSkillCacheStateRuntime.buffSkillIds(entry).contains(skillId), "unexpected cached buff " + skillId);
            assertFalse(AgentCombatSkillCacheStateRuntime.attackSkillId(entry) == skillId
                    || AgentCombatSkillCacheStateRuntime.aoeSkillId(entry) == skillId,
                    "unexpected cached attack " + skillId);
        }
    }

    private static Skill skillWithAnchoredAoe(int skillId, int attackCount, int mobCount, int damage) {
        Skill skill = skillWithAttack(skillId, attackCount, mobCount, damage);
        StatEffect effect = skill.getEffect(1);
        when(effect.hasBoundingBox()).thenReturn(true);
        when(effect.calculateBoundingBox(any(Point.class), anyBoolean())).thenAnswer(invocation -> {
            Point anchor = invocation.getArgument(0);
            return new Rectangle(anchor.x - 30, anchor.y - 50, 80, 100);
        });
        return skill;
    }

}
