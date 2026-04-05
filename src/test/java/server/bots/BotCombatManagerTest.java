package server.bots;

import client.BuffStat;
import client.Character;
import client.Job;
import client.Skill;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.WeaponType;
import net.packet.Packet;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import server.StatEffect;
import server.life.Monster;
import server.maps.MapleMap;

import java.awt.*;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotCombatManagerTest {
    @Test
    void shouldMatchOpenStoryBasicAttackStanceIds() {
        assertEquals(23, BotCombatManager.attackStanceId("swingO1"));
        assertEquals(11, BotCombatManager.attackStanceId("shoot1"));
        assertEquals(10, BotCombatManager.attackStanceId("shot"));
        assertEquals(0, BotCombatManager.attackStanceId("stand1"));
    }

    @Test
    void shouldUseOpenStoryFallbackAttackGroupsByWeaponType() {
        BotCombatManager.BasicAttackSpec gunSpec = BotCombatManager.basicAttackSpec(WeaponType.GUN);
        assertEquals(9, gunSpec.display());
        assertEquals("handgun", gunSpec.primaryAction());

        BotCombatManager.BasicAttackSpec bowSpec = BotCombatManager.basicAttackSpec(WeaponType.BOW);
        assertEquals(3, bowSpec.display());
        assertEquals("shoot1", bowSpec.primaryAction());

        BotCombatManager.BasicAttackSpec twoHandedSpec = BotCombatManager.basicAttackSpec(WeaponType.SWORD2H);
        assertEquals(5, twoHandedSpec.display());
        assertTrue(twoHandedSpec.actions().contains("swingT1"));
        assertTrue(twoHandedSpec.actions().contains("stabO1"));

        BotCombatManager.BasicAttackSpec degenerateBowSpec = BotCombatManager.basicAttackSpec(WeaponType.BOW, true);
        assertEquals(3, degenerateBowSpec.display());
        assertTrue(degenerateBowSpec.actions().contains("swingT1"));
        assertTrue(degenerateBowSpec.actions().contains("swingT3"));
    }

    @Test
    void shouldUseExplicitSkillActionBeforeWeaponFallback() {
        Skill skill = new Skill(3121004);
        skill.setAction0("doublefire");

        String action = BotCombatManager.resolveSkillAttackAction(null, skill, 1, WeaponType.BOW);

        assertEquals("doublefire", action);
    }

    @Test
    void shouldTreatBasicStaffAttacksAsCloseRange() {
        assertEquals(BotCombatManager.AttackRoute.CLOSE, BotCombatManager.determineBasicWeaponRoute(WeaponType.STAFF));
    }

    @Test
    void shouldScalePhysicalSkillDamageFromBaseWeaponRange() {
        Character bot = mockDamageBot();
        StatEffect effect = mock(StatEffect.class);
        when(bot.getTotalWatk()).thenReturn(100);
        when(bot.calculateMaxBaseDamage(100)).thenReturn(1_000);
        when(bot.calculateMinBaseDamage(100)).thenReturn(500);
        when(effect.getDamage()).thenReturn(260);

        BotCombatManager.DamageProfile profile = BotCombatManager.resolveDamageProfile(bot, 1001005, effect, false);

        assertEquals(1_300, profile.minDamage());
        assertEquals(2_600, profile.maxDamage());
        assertFalse(profile.magicAttack());
        assertFalse(profile.alwaysHit());
    }

    @Test
    void shouldUseThrowingSkillBaseFormula() {
        Character bot = mockDamageBot();
        StatEffect effect = mock(StatEffect.class);
        when(bot.getTotalWatk()).thenReturn(90);
        when(bot.getTotalLuk()).thenReturn(200);
        when(effect.getDamage()).thenReturn(150);

        BotCombatManager.DamageProfile profile =
                BotCombatManager.resolveDamageProfile(bot, constants.skills.Rogue.LUCKY_SEVEN, effect, false);

        assertEquals(1_200, profile.minDamage());
        assertEquals(1_500, profile.maxDamage());
    }

    @Test
    void shouldUseMagicBaseDamageForMagicSkills() {
        Character bot = mockDamageBot();
        StatEffect effect = mock(StatEffect.class);
        when(bot.getTotalMagic()).thenReturn(250);
        when(bot.calculateMaxBaseMagicDamage(250)).thenReturn(600);
        when(effect.getMatk()).thenReturn((short) 3);

        BotCombatManager.DamageProfile profile =
                BotCombatManager.resolveDamageProfile(bot, 2101004, effect, true);

        assertEquals(1_440, profile.minDamage());
        assertEquals(1_800, profile.maxDamage());
        assertTrue(profile.magicAttack());
        assertFalse(profile.alwaysHit());
    }

    @Test
    void shouldTreatFixedDamageSkillsAsAlwaysHit() {
        Character bot = mockDamageBot();
        StatEffect effect = mock(StatEffect.class);
        when(effect.getFixDamage()).thenReturn(777);

        BotCombatManager.DamageProfile profile = BotCombatManager.resolveDamageProfile(bot, 0, effect, false);

        assertEquals(777, profile.minDamage());
        assertEquals(777, profile.maxDamage());
        assertTrue(profile.alwaysHit());
    }

    @Test
    void shouldMatchOpenStoryGroundMobKnockbackWhenHitFromRight() {
        MapleMap map = mock(MapleMap.class);
        Character bot = mockBot(new Point(100, 200), map, 20_000, null);
        Monster mob = mockMob(new Point(140, 200), 9300000);
        BotEntry entry = new BotEntry(bot, null, null);

        BotCombatManager.applyMobHit(entry, bot, mob);

        assertTrue(entry.inAir);
        assertFalse(entry.climbing);
        assertEquals(new Point(100, 200), bot.getPosition());
        assertEquals(-Math.round(1.5f * BotMovementManager.cfg.TICK_MS / 8.0f), entry.airVelX);
        assertEquals(-3.5f * BotMovementManager.cfg.TICK_MS / 8.0f, entry.velY, 1.0e-4f);
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

        BotCombatManager.applyMobHit(entry, bot, mob);

        assertTrue(entry.inAir);
        assertEquals(new Point(100, 200), bot.getPosition());
        assertEquals(12.5f, entry.velY, 1.0e-4f);
        assertEquals(Math.round(1.5f * BotMovementManager.cfg.TICK_MS / 8.0f), entry.airVelX);
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

        assertEquals(BotPhysicsEngine.cfg.DEAD_LEFT_STANCE, bot.getStance());
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
    void shouldFallbackToBasicAttackTimingWhenSkillAnimationDelayMissing() {
        BotCombatManager.SkillAttackTiming timing =
                BotCombatManager.resolveSkillAttackTiming(0, 4, 4, 300, 590);

        assertEquals(300, timing.hitDelayMs());
        assertEquals(590, timing.cooldownMs());
    }

    @Test
    void shouldNotUnlockSkillFasterThanBasicAttackCooldown() {
        BotCombatManager.SkillAttackTiming timing =
                BotCombatManager.resolveSkillAttackTiming(450, 4, 4, 300, 590);

        assertEquals(173, timing.hitDelayMs());
        assertEquals(590, timing.cooldownMs());
    }

    @Test
    void shouldApplyAttackSpeedScalingToSkillAnimationTiming() {
        BotCombatManager.SkillAttackTiming timing =
                BotCombatManager.resolveSkillAttackTiming(520, 4, 2, 120, 0);

        assertEquals(173, timing.hitDelayMs());
        assertEquals(BotMovementManager.delayAfterCurrentTick(347), timing.cooldownMs());
    }

    @Test
    void shouldUseDegenerateCloseAttackPoolForBowAtPointBlankRange() {
        assertTrue(BotCombatManager.shouldDegenerateRangedAttack(WeaponType.BOW, new Point(100, 200), new Point(145, 200)));
        BotCombatManager.BasicAttackSpec bowSpec = BotCombatManager.basicAttackSpec(WeaponType.BOW, true);

        assertEquals(3, bowSpec.display());
        assertTrue(bowSpec.actions().contains("swingT1"));
        assertTrue(bowSpec.actions().contains("swingT3"));
    }

    @Test
    void shouldKeepBowOutOfDegenerateModeWhenTargetIsNotCrowding() {
        assertFalse(BotCombatManager.shouldDegenerateRangedAttack(WeaponType.BOW, new Point(100, 200), new Point(300, 200)));
        BotCombatManager.BasicAttackSpec bowSpec = BotCombatManager.basicAttackSpec(WeaponType.BOW, false);

        assertEquals("shoot1", bowSpec.primaryAction());
    }

    @Test
    void shouldRetreatFromNearbyRangedTargetsOutsideMeleeReach() {
        assertTrue(BotCombatManager.shouldRetreatFromNearbyTarget(WeaponType.BOW, new Point(100, 200), new Point(220, 200)));
        assertEquals(new Point(-20, 200), BotCombatManager.retreatTargetPosition(new Point(100, 200), new Point(220, 200)));
        assertFalse(BotCombatManager.shouldRetreatFromNearbyTarget(WeaponType.BOW, new Point(100, 200), new Point(145, 200)));
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
    void shouldRejectJumpAttackWhenTargetIsTooHighOrTooFar() {
        assertFalse(BotCombatManager.isTargetJumpable(true, new Point(100, 200), new Point(241, 135)));
        assertFalse(BotCombatManager.isTargetJumpable(true, new Point(100, 200), new Point(170, 60)));
    }

    private static void assertDamageDirection(MapleMap map, Character bot, int expectedBroadcasts, int expectedDirection) {
        ArgumentCaptor<Packet> packets = ArgumentCaptor.forClass(Packet.class);
        verify(map, times(expectedBroadcasts)).broadcastMessage(eq(bot), packets.capture(), eq(false));
        byte[] payload = packets.getAllValues().get(0).getBytes();
        assertEquals(expectedDirection, Byte.toUnsignedInt(payload[15]));
    }

    private static Character mockDamageBot() {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.BEGINNER);
        return bot;
    }

    private static Character mockBot(Point startPosition, MapleMap map, int startingHp, Integer stancePercent) {
        Character bot = mock(Character.class);
        Inventory equipped = mock(Inventory.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(startPosition));
        AtomicInteger hp = new AtomicInteger(startingHp);
        AtomicInteger stance = new AtomicInteger(BotPhysicsEngine.cfg.STAND_RIGHT_STANCE);

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
        when(bot.getJob()).thenReturn(Job.BEGINNER);
        when(bot.getLevel()).thenReturn(200);
        when(bot.getTotalWdef()).thenReturn(0);
        when(bot.getTotalStr()).thenReturn(4);
        when(bot.getTotalDex()).thenReturn(4);
        when(bot.getTotalInt()).thenReturn(4);
        when(bot.getTotalLuk()).thenReturn(4);
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
        when(mob.getPADamage()).thenReturn(1_000);
        when(mob.getLevel()).thenReturn(1);
        when(mob.getAccuracy()).thenReturn(9_999);
        when(mob.isAlive()).thenReturn(true);
        return mob;
    }
}
