package server.bots;

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
import constants.skills.Beginner;
import constants.skills.Cleric;
import constants.skills.Hunter;
import constants.skills.Magician;
import constants.skills.Warrior;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import server.StatEffect;
import server.bots.combat.BotAttackDataProvider;
import server.life.Monster;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.*;
import java.lang.reflect.Field;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import tools.HexTool;

class BotCombatManagerTest {
    @Test
    void shouldMatchOpenStoryBasicAttackStanceIds() {
        BotAttackDataProvider provider = BotAttackDataProvider.getInstance();
        assertEquals(23, provider.getAttackStanceId("swingO1"));
        assertEquals(11, provider.getAttackStanceId("shoot1"));
        assertEquals(10, provider.getAttackStanceId("shot"));
        assertEquals(0, provider.getAttackStanceId("stand1"));
    }

    @Test
    void shouldUseOpenStoryFallbackAttackGroupsByWeaponType() {
        BotAttackDataProvider provider = BotAttackDataProvider.getInstance();
        BotAttackDataProvider.AttackAnimationSpec gunSpec = provider.getBasicAttackSpec(WeaponType.GUN);
        assertEquals(9, gunSpec.display());
        assertEquals("handgun", gunSpec.primaryAction());

        BotAttackDataProvider.AttackAnimationSpec bowSpec = provider.getBasicAttackSpec(WeaponType.BOW);
        assertEquals(3, bowSpec.display());
        assertEquals("shoot1", bowSpec.primaryAction());

        BotAttackDataProvider.AttackAnimationSpec twoHandedSpec = provider.getBasicAttackSpec(WeaponType.SWORD2H);
        assertEquals(5, twoHandedSpec.display());
        assertTrue(twoHandedSpec.actions().contains("swingT1"));
        assertTrue(twoHandedSpec.actions().contains("stabO1"));

        BotAttackDataProvider.AttackAnimationSpec degenerateBowSpec = provider.getBasicAttackSpec(WeaponType.BOW, true);
        assertEquals(3, degenerateBowSpec.display());
        assertTrue(degenerateBowSpec.actions().contains("swingT1"));
        assertTrue(degenerateBowSpec.actions().contains("swingT3"));
    }

    @Test
    void shouldUseExplicitSkillActionBeforeWeaponFallback() {
        Skill skill = new Skill(3121004);
        skill.setAction0("doublefire");

        String action = BotAttackExecutionProvider.resolveSkillAttackAction(null, skill, 1, WeaponType.BOW);

        assertEquals("doublefire", action);
    }

    @Test
    void shouldMatchRealMagicGuardSpecialMovePacketLayout() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);

        byte[] packet = BotCombatManager.buildSupportSpecialMovePacket(bot, Magician.MAGIC_GUARD, 20, 0x009195A5);

        assertArrayEquals(HexTool.toBytes("5B 00 A5 95 91 00 6A 88 1E 00 14 00 00"), packet);
    }

    @Test
    void shouldMatchRealBlessSpecialMovePacketLayout() {
        Character bot = mockBot(new Point(0x155D, 0x01C6), mock(MapleMap.class), 20_000, null);
        when(bot.isFacingLeft()).thenReturn(true);

        byte[] packet = BotCombatManager.buildSupportSpecialMovePacket(bot, Cleric.BLESS, 9, 0x00919AAF);

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

        BotEntry entry = new BotEntry(bot, null, null);
        BotCombatManager.rebuildSkillCacheIfNeeded(entry, bot);

        assertEquals(Warrior.POWER_STRIKE, entry.attackSkillId);
        assertEquals(Warrior.SLASH_BLAST, entry.aoeSkillId);
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

        BotEntry entry = new BotEntry(bot, null, null);
        entry.attackSkillId = powerStrike.getId();
        entry.aoeSkillId = slashBlast.getId();

        try (MockedStatic<SkillFactory> skillFactory = Mockito.mockStatic(SkillFactory.class)) {
            skillFactory.when(() -> SkillFactory.getSkill(powerStrike.getId())).thenReturn(powerStrike);
            skillFactory.when(() -> SkillFactory.getSkill(slashBlast.getId())).thenReturn(slashBlast);

            BotCombatManager.AttackPlan plan = BotCombatManager.planAttack(entry, bot, primary);

            assertEquals(Warrior.POWER_STRIKE, plan.skillId);
            assertEquals(List.of(primary), plan.targets);
        }
    }

    @Test
    void shouldCapSingleTargetOverkillWhenAoeDoesMoreUsefulDamage() {
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

        BotEntry entry = new BotEntry(bot, null, null);
        entry.attackSkillId = powerStrike.getId();
        entry.aoeSkillId = slashBlast.getId();

        try (MockedStatic<SkillFactory> skillFactory = Mockito.mockStatic(SkillFactory.class)) {
            skillFactory.when(() -> SkillFactory.getSkill(powerStrike.getId())).thenReturn(powerStrike);
            skillFactory.when(() -> SkillFactory.getSkill(slashBlast.getId())).thenReturn(slashBlast);

            BotCombatManager.AttackPlan plan = BotCombatManager.planAttack(entry, bot, primary);

            assertEquals(Warrior.SLASH_BLAST, plan.skillId);
            assertEquals(List.of(primary, secondary), plan.targets);
        }
    }

    @Test
    void shouldTreatBasicStaffAttacksAsCloseRange() {
        assertEquals(BotCombatManager.AttackRoute.CLOSE, BotAttackExecutionProvider.determineBasicWeaponRoute(WeaponType.STAFF));
    }

    @Test
    void shouldMatchOpenStoryGroundMobKnockbackWhenHitFromRight() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Monster mob = mockMob(new Point(140, 200), 9300000);
        BotEntry entry = new BotEntry(bot, null, null);
        entry.facingDir = 1;

        BotCombatManager.applyMobHit(entry, bot, mob);

        assertTrue(entry.inAir);
        assertFalse(entry.climbing);
        assertTrue(entry.climbUpIntent);
        assertEquals(new Point(100, 200), bot.getPosition());
        assertEquals(-Math.round(1.5f * BotMovementManager.cfg.TICK_MS / 8.0f), entry.airVelX);
        assertEquals(-3.5f * BotMovementManager.cfg.TICK_MS / 8.0f, entry.velY, 1.0e-4f);
        assertEquals(1, entry.facingDir);
        assertEquals(CharacterStance.JUMP_RIGHT_STANCE, bot.getStance());
        assertDamageDirection(map, bot, 2, 0);
    }

    @Test
    void shouldOnlyRedirectHorizontalVelocityWhenMobHitOccursMidAir() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Monster mob = mockMob(new Point(60, 200), 9300001);
        BotEntry entry = new BotEntry(bot, null, null);
        entry.inAir = true;
        entry.physX = 100;
        entry.physY = 200;
        entry.velY = 12.5f;
        entry.airVelX = -4;
        entry.facingDir = -1;

        BotCombatManager.applyMobHit(entry, bot, mob);

        assertTrue(entry.inAir);
        assertTrue(entry.climbUpIntent);
        assertEquals(new Point(100, 200), bot.getPosition());
        assertEquals(12.5f, entry.velY, 1.0e-4f);
        assertEquals(Math.round(1.5f * BotMovementManager.cfg.TICK_MS / 8.0f), entry.airVelX);
        assertEquals(-1, entry.facingDir);
        assertEquals(CharacterStance.JUMP_LEFT_STANCE, bot.getStance());
        assertDamageDirection(map, bot, 2, 1);
    }

    @Test
    void shouldSkipMobKnockbackWhenStanceBuffIsMaxed() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, 100);
        Monster mob = mockMob(new Point(140, 200), 9300002);
        BotEntry entry = new BotEntry(bot, null, null);

        BotCombatManager.applyMobHit(entry, bot, mob);

        assertFalse(entry.inAir);
        assertFalse(entry.climbing);
        assertEquals(new Point(100, 200), bot.getPosition());
        assertEquals(0, entry.airVelX);
        assertEquals(0.0f, entry.velY, 1.0e-4f);
        assertDamageDirection(map, bot, 1, 0);
    }

    @Test
    void shouldKeepDirectionAwareDeadStanceAfterFatalMobHit() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 1, null);
        BotEntry entry = new BotEntry(bot, null, null);
        entry.facingDir = -1;
        Monster mob = mockMob(new Point(140, 200), 9300003);

        BotCombatManager.applyMobHit(entry, bot, mob);

        assertEquals(CharacterStance.DEAD_LEFT_STANCE, bot.getStance());
        assertTrue(entry.deadUntil > 0);
        assertFalse(entry.inAir);
        assertFalse(entry.climbing);
    }

    @Test
    void shouldCreateFallbackHitBoxForCloseRangeSkillWithoutBoundingBox() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        StatEffect effect = mock(StatEffect.class);
        when(effect.getRange()).thenReturn(0);

        Rectangle hitBox = BotCombatManager.fallbackCloseRangeSkillHitBox(effect, bot, false);

        assertEquals(new Rectangle(100, 150, 80, 70), hitBox);
    }

    @Test
    void shouldExpandFallbackHitBoxUsingSkillRange() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        StatEffect effect = mock(StatEffect.class);
        when(effect.getRange()).thenReturn(130);

        Rectangle hitBox = BotCombatManager.fallbackCloseRangeSkillHitBox(effect, bot, true);

        assertEquals(new Rectangle(-30, 150, 130, 70), hitBox);
    }

    @Test
    void shouldCreateFallbackHitBoxForRangedSkillWithoutBoundingBox() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        StatEffect effect = mock(StatEffect.class);
        when(effect.getRange()).thenReturn(0);

        Rectangle hitBox = BotCombatManager.fallbackSkillHitBox(effect, bot, false, BotCombatManager.AttackRoute.RANGED);

        assertEquals(new Rectangle(105, 150, 395, 100), hitBox);
    }

    @Test
    void shouldCreateFallbackHitBoxForMagicSkillWithoutBoundingBox() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        StatEffect effect = mock(StatEffect.class);
        when(effect.getRange()).thenReturn(0);

        Rectangle hitBox = BotCombatManager.fallbackSkillHitBox(effect, bot, true, BotCombatManager.AttackRoute.MAGIC);

        assertEquals(new Rectangle(-300, 150, 395, 100), hitBox);
    }

    @Test
    void shouldScaleFallbackProjectileHitBoxUsingSkillRangeMultiplier() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        StatEffect effect = mock(StatEffect.class);
        when(effect.getRange()).thenReturn(150);

        Rectangle hitBox = BotCombatManager.fallbackSkillHitBox(effect, bot, false, BotCombatManager.AttackRoute.MAGIC);

        assertEquals(new Rectangle(105, 150, 595, 100), hitBox);
    }

    @Test
    void shouldAddPassiveRangeSkillsToProjectileRange() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Skill eyeOfAmazon = new Skill(Archer.EYE_OF_AMAZON);
        StatEffect effect = mock(StatEffect.class);
        when(effect.getRange()).thenReturn(120);
        eyeOfAmazon.addLevelEffect(effect);

        Map<Skill, Character.SkillEntry> skills = new LinkedHashMap<>();
        skills.put(eyeOfAmazon, null);
        when(bot.getSkills()).thenReturn(skills);
        when(bot.getSkillLevel(eyeOfAmazon)).thenReturn((byte) 1);

        Rectangle hitBox = BotCombatManager.clientProjectileHitBox(bot, false, 1.0f);

        assertEquals(new Rectangle(105, 150, 515, 100), hitBox);
    }

    @Test
    void shouldFallbackToBasicAttackTimingWhenSkillAnimationDelayMissing() {
        BotAttackExecutionProvider.SkillAttackTiming timing =
                BotAttackExecutionProvider.resolveSkillAttackTiming(0, 4, 300, 590);

        assertEquals(300, timing.hitDelayMs());
        assertEquals(590, timing.cooldownMs());
    }

    @Test
    void shouldNotUnlockSkillFasterThanBasicAttackCooldown() {
        BotAttackExecutionProvider.SkillAttackTiming timing =
                BotAttackExecutionProvider.resolveSkillAttackTiming(450, 4, 300, 590);

        assertEquals(173, timing.hitDelayMs());
        assertEquals(590, timing.cooldownMs());
    }

    @Test
    void shouldApplyAttackSpeedScalingToSkillAnimationTiming() {
        BotAttackExecutionProvider.SkillAttackTiming timing =
                BotAttackExecutionProvider.resolveSkillAttackTiming(520, 2, 120, 0);

        assertEquals(173, timing.hitDelayMs());
        assertEquals(347, timing.cooldownMs());
    }

    @Test
    void shouldUseDegenerateCloseAttackPoolForBowAtPointBlankRange() {
        assertTrue(BotAttackExecutionProvider.shouldDegenerateRangedAttack(WeaponType.BOW, new Point(100, 200), new Point(145, 200)));
        BotAttackDataProvider.AttackAnimationSpec bowSpec = BotAttackDataProvider.getInstance().getBasicAttackSpec(WeaponType.BOW, true);

        assertEquals(3, bowSpec.display());
        assertTrue(bowSpec.actions().contains("swingT1"));
        assertTrue(bowSpec.actions().contains("swingT3"));
    }

    @Test
    void shouldKeepBowOutOfDegenerateModeWhenTargetIsNotCrowding() {
        assertFalse(BotAttackExecutionProvider.shouldDegenerateRangedAttack(WeaponType.BOW, new Point(100, 200), new Point(300, 200)));
        BotAttackDataProvider.AttackAnimationSpec bowSpec = BotAttackDataProvider.getInstance().getBasicAttackSpec(WeaponType.BOW, false);

        assertEquals("shoot1", bowSpec.primaryAction());
    }

    @Test
    void shouldRetreatFromNearbyRangedTargetsInsideRetreatBand() {
        Point botPos = new Point(100, 200);
        Point retreatBandTarget = new Point(botPos.x + BotCombatManager.cfg.RANGED_RETREAT_THRESHOLD_X, 200);
        Point pointBlankTarget = new Point(botPos.x + 1, 200);
        Point justOutsideRetreatTarget = new Point(botPos.x + BotCombatManager.cfg.RANGED_RETREAT_THRESHOLD_X + 1, 200);
        Point farTarget = new Point(botPos.x + BotCombatManager.cfg.RANGED_DEGENERATE_RANGE_X + 100, 200);

        assertTrue(BotAttackExecutionProvider.shouldRetreatFromNearbyTarget(WeaponType.BOW, botPos, retreatBandTarget));
        assertEquals(new Point(botPos.x - BotCombatManager.cfg.RANGED_RETREAT_DISTANCE_X, 200),
                BotAttackExecutionProvider.retreatTargetPosition(botPos, retreatBandTarget));
        assertTrue(BotAttackExecutionProvider.shouldRetreatFromNearbyTarget(WeaponType.BOW, botPos, pointBlankTarget));
        assertFalse(BotAttackExecutionProvider.shouldRetreatFromNearbyTarget(WeaponType.BOW, botPos, justOutsideRetreatTarget));
        assertFalse(BotAttackExecutionProvider.shouldRetreatFromNearbyTarget(WeaponType.BOW, botPos, farTarget));
    }

    @Test
    void shouldAllowDiagonalJumpAttackForCloseRangeTargetsSlightlyAbove() {
        assertTrue(BotCombatManager.isTargetJumpable(true, new Point(100, 200), new Point(230, 135)));
    }

    @Test
    void shouldRejectJumpAttackForNonCloseRangeRoutes() {
        assertFalse(BotCombatManager.isTargetJumpable(false, new Point(100, 200), new Point(170, 135)));
    }

    @Test
    void shouldRejectAirborneRangedAttackPlansForWeaponsThatCannotJumpShoot() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        BotEntry entry = new BotEntry(bot, null, null);
        entry.inAir = true;
        BotCombatManager.AttackPlan rangedBowPlan = new BotCombatManager.AttackPlan(
                Hunter.ARROW_BOMB, 1, 1, new Rectangle(100, 150, 300, 100),
                List.of(mockMob(new Point(180, 200), 9300200)), BotCombatManager.AttackRoute.RANGED,
                0, 11, 11, 11, 4, 300, 600);
        BotCombatManager.AttackPlan closePlan = new BotCombatManager.AttackPlan(
                0, 0, 1, new Rectangle(100, 150, 80, 70),
                List.of(mockMob(new Point(120, 200), 9300201)), BotCombatManager.AttackRoute.CLOSE,
                4, 1, 1, 0, 4, 300, 600);

        assertFalse(BotCombatManager.canUseAttackPlanNow(entry, WeaponType.BOW, rangedBowPlan));
        assertFalse(BotCombatManager.canUseAttackPlanNow(entry, WeaponType.CROSSBOW, rangedBowPlan));
        assertFalse(BotCombatManager.canUseAttackPlanNow(entry, WeaponType.GUN, rangedBowPlan));
        assertTrue(BotCombatManager.canUseAttackPlanNow(entry, WeaponType.CLAW, rangedBowPlan));
        assertTrue(BotCombatManager.canUseAttackPlanNow(entry, WeaponType.BOW, closePlan));
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
        BotEntry entry = new BotEntry(bot, null, null);
        entry.aoeSkillId = Hunter.ARROW_BOMB;

        try (MockedStatic<SkillFactory> skillFactory = Mockito.mockStatic(SkillFactory.class);
             MockedStatic<BotAttackExecutionProvider> attackExecution =
                     Mockito.mockStatic(BotAttackExecutionProvider.class, Mockito.CALLS_REAL_METHODS)) {
            skillFactory.when(() -> SkillFactory.getSkill(Hunter.ARROW_BOMB)).thenReturn(arrowBomb);
            attackExecution.when(() -> BotAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.BOW);

            BotCombatManager.AttackPlan plan = BotCombatManager.planAttack(entry, bot, farSelected);

            assertEquals(Hunter.ARROW_BOMB, plan.skillId);
            assertEquals(closest, plan.targets.get(0));
            assertTrue(plan.targets.contains(splash));
            assertFalse(plan.targets.contains(farSelected));
        }
    }

    @Test
    void shouldRejectJumpAttackWhenTargetIsTooHighOrTooFar() {
        assertFalse(BotCombatManager.isTargetJumpable(true, new Point(100, 200), new Point(241, 135)));
        assertFalse(BotCombatManager.isTargetJumpable(true, new Point(100, 200), new Point(170, 60)));
    }

    @Test
    void shouldUseClientStyleMobTouchSweepForStationaryBot() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        BotEntry entry = new BotEntry(bot, null, null);

        Rectangle bounds = BotCombatManager.getBotTouchBounds(entry, bot);

        assertEquals(new Rectangle(100, 150, 1, 51), bounds);
    }

    @Test
    void shouldIgnoreMobThatOnlyBrushesBotSpriteSide() {
        Character bot = mockBot(new Point(100, 200), mock(MapleMap.class), 20_000, null);
        BotEntry entry = new BotEntry(bot, null, null);
        Monster mob = mockMob(new Point(122, 200), 100100);
        when(mob.isFacingLeft()).thenReturn(false);

        assertFalse(BotCombatManager.isMobTouchingBot(entry, bot, mob));
    }

    @Test
    void shouldDetectMobTouchAcrossBotMovementSweep() {
        Character bot = mockBot(new Point(120, 200), mock(MapleMap.class), 20_000, null);
        BotEntry entry = new BotEntry(bot, null, null);
        entry.lastMobTouchCheckPos = new Point(80, 200);
        entry.lastMobTouchMapId = 0;
        when(bot.getMapId()).thenReturn(0);
        Monster mob = mockMob(new Point(96, 200), 100100);
        when(mob.isFacingLeft()).thenReturn(false);

        assertTrue(BotCombatManager.isMobTouchingBot(entry, bot, mob));
    }

    @Test
    void shouldPreferCurrentFootholdTargetBeforePathScoringOtherRegions() {
        MapleMap map = spy(new MapleMap(910009050, 0, 0, 910009050, 1.0f));
        server.maps.FootholdTree footholds = new server.maps.FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(0, 100), new Point(300, 100), 1));
        footholds.insert(new Foothold(new Point(0, 200), new Point(300, 200), 2));
        map.setFootholds(footholds);
        BotNavigationGraphProvider.rebuildGraph(map);

        Character bot = mockBot(new Point(100, 100), map, 20_000, null);
        Monster currentFootholdMob = mockMob(new Point(180, 100), 100100);
        Monster otherRegionMob = mockMob(new Point(105, 200), 100100);
        doReturn(List.of(otherRegionMob, currentFootholdMob)).when(map).getAllMonsters();

        Monster target = BotCombatManager.findGrindTarget(new BotEntry(bot, null, null), bot);

        assertEquals(currentFootholdMob, target);
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

        BotNavigationGraph.Region startRegion = new BotNavigationGraph.Region(
                1, List.of(new BotNavigationGraph.Segment(startFoothold)));
        BotNavigationGraph.Region occupiedRegion = new BotNavigationGraph.Region(
                2, List.of(new BotNavigationGraph.Segment(occupiedFoothold)));
        BotNavigationGraph.Region openRegion = new BotNavigationGraph.Region(
                3, List.of(new BotNavigationGraph.Segment(openFoothold)));
        BotNavigationGraph graph = new BotNavigationGraph(
                map.getId(),
                1,
                BotMovementProfile.base(),
                List.of(startRegion, occupiedRegion, openRegion),
                Map.of(1, startRegion, 2, occupiedRegion, 3, openRegion),
                Map.of(1, 1, 2, 2, 3, 3),
                Map.of(1, List.of(
                        new BotNavigationGraph.Edge(1, 2, BotNavigationGraph.EdgeType.WALK,
                                new Point(100, 100), new Point(200, 100), 0, 0, 0, 0, 0, 100),
                        new BotNavigationGraph.Edge(1, 3, BotNavigationGraph.EdgeType.WALK,
                                new Point(100, 100), new Point(320, 100), 0, 0, 0, 0, 0, 150))),
                Set.of());

        Character owner = mock(Character.class);
        when(owner.getId()).thenReturn(909052);
        Character bot = mockBot(new Point(50, 100), map, 20_000, null);
        Character siblingBot = mockBot(new Point(220, 100), map, 20_000, null);
        Monster occupiedTarget = mockMob(new Point(220, 100), 9300400);
        Monster openTarget = mockMob(new Point(340, 100), 9300401);
        doReturn(List.of(occupiedTarget, openTarget)).when(map).getAllMonsters();

        BotEntry entry = new BotEntry(bot, owner, null);
        entry.grinding = true;
        BotEntry siblingEntry = new BotEntry(siblingBot, owner, null);
        siblingEntry.grinding = true;

        BotManager manager = BotManager.getInstance();
        Map<Integer, List<BotEntry>> bots = botEntries(manager);
        bots.put(owner.getId(), new CopyOnWriteArrayList<>(List.of(entry, siblingEntry)));
        try (MockedStatic<BotNavigationGraphProvider> graphProvider =
                     Mockito.mockStatic(BotNavigationGraphProvider.class, Mockito.CALLS_REAL_METHODS)) {
            graphProvider.when(() -> BotNavigationGraphProvider.peekGraph(map, BotMovementProfile.base()))
                    .thenReturn(graph);

            Monster target = BotCombatManager.findGrindTarget(entry, bot);

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
        BotNavigationGraphProvider.rebuildGraph(map);

        Character bot = mockBot(new Point(100, 100), map, 20_000, null);
        Monster otherRegionMob = mockMob(new Point(300, 130), 100100);
        doReturn(List.of(otherRegionMob)).when(map).getAllMonsters();

        BotEntry entry = new BotEntry(bot, null, null);

        try (MockedStatic<BotAttackExecutionProvider> attackExecution =
                     Mockito.mockStatic(BotAttackExecutionProvider.class, Mockito.CALLS_REAL_METHODS)) {
            attackExecution.when(() -> BotAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.BOW);

            assertEquals(otherRegionMob, BotCombatManager.findFollowAttackTarget(entry, bot));
            assertEquals(otherRegionMob, BotCombatManager.findGrindTarget(entry, bot));
            assertTrue(BotCombatManager.isReachableGrindTarget(entry, bot, otherRegionMob));
        }
    }

    private static void assertDamageDirection(MapleMap map, Character bot, int expectedBroadcasts, int expectedDirection) {
        ArgumentCaptor<Packet> packets = ArgumentCaptor.forClass(Packet.class);
        verify(map, times(expectedBroadcasts)).broadcastMessage(eq(bot), packets.capture(), eq(false));
        byte[] payload = packets.getAllValues().get(0).getBytes();
        assertEquals(expectedDirection, Byte.toUnsignedInt(payload[15]));
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
        when(mob.isAlive()).thenReturn(true);
        return mob;
    }

    private static Skill skillWithAttack(int skillId, int attackCount, int mobCount, int damage) {
        Skill skill = new Skill(skillId);
        StatEffect effect = mock(StatEffect.class);
        when(effect.getAttackCount()).thenReturn(attackCount);
        when(effect.getMobCount()).thenReturn(mobCount);
        when(effect.getDamage()).thenReturn(damage);
        when(effect.getDuration()).thenReturn(0);
        when(effect.getMpCon()).thenReturn((short) 1);
        when(effect.canPaySkillCost(any(Character.class))).thenReturn(true);
        skill.addLevelEffect(effect);
        return skill;
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

    @SuppressWarnings("unchecked")
    private static Map<Integer, List<BotEntry>> botEntries(BotManager manager) throws Exception {
        Field field = BotManager.class.getDeclaredField("bots");
        field.setAccessible(true);
        return (Map<Integer, List<BotEntry>>) field.get(manager);
    }
}
