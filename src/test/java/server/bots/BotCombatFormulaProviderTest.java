package server.bots;

import client.BuffStat;
import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.StatEffect;
import server.bots.combat.BotCombatFormulaProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotCombatFormulaProviderTest {
    private final BotCombatFormulaProvider provider = BotCombatFormulaProvider.getInstance();

    @Test
    void shouldIncludeDerivedEquipAndBuffAccuracy() {
        Character bot = mock(Character.class);
        Inventory equipped = mock(Inventory.class);
        Equip weapon = mock(Equip.class);
        Equip glove = mock(Equip.class);

        when(bot.getTotalDex()).thenReturn(100);
        when(bot.getTotalLuk()).thenReturn(50);
        when(bot.getBuffedValue(BuffStat.ACC)).thenReturn(7);
        when(weapon.getAcc()).thenReturn((short) 12);
        when(glove.getAcc()).thenReturn((short) 4);
        when(bot.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(equipped.iterator()).thenReturn(List.<client.inventory.Item>of(weapon, glove).iterator());

        assertEquals(128, provider.getTotalAccuracy(bot));
    }

    @Test
    void shouldUseIntAndLukForMagicAccuracyOnly() {
        Character bot = mock(Character.class);
        Inventory equipped = mock(Inventory.class);
        Equip weapon = mock(Equip.class);

        when(bot.getTotalInt()).thenReturn(123);
        when(bot.getTotalLuk()).thenReturn(47);
        when(bot.getBuffedValue(BuffStat.ACC)).thenReturn(50);
        when(weapon.getAcc()).thenReturn((short) 40);
        when(bot.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(equipped.iterator()).thenReturn(List.<client.inventory.Item>of(weapon).iterator());

        assertEquals(80, provider.getTotalMagicAccuracy(bot));
    }

    @Test
    void shouldIncludeDerivedEquipAndBuffAvoidability() {
        Character bot = mock(Character.class);
        Inventory equipped = mock(Inventory.class);
        Equip cape = mock(Equip.class);
        Equip shoes = mock(Equip.class);

        when(bot.getTotalDex()).thenReturn(100);
        when(bot.getTotalLuk()).thenReturn(50);
        when(bot.getBuffedValue(BuffStat.AVOID)).thenReturn(7);
        when(cape.getAvoid()).thenReturn((short) 12);
        when(shoes.getAvoid()).thenReturn((short) 4);
        when(bot.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(equipped.iterator()).thenReturn(List.<client.inventory.Item>of(cape, shoes).iterator());

        assertEquals(73, provider.getTotalAvoidability(bot));
    }

    @Test
    void shouldMatchOpenStoryMobHitChanceFormula() {
        double hitChance = provider.calculatePhysicalMobHitChance(80, 50, 55, 20);

        assertEquals(0.8958333333333335d, hitChance, 1.0e-12d);
    }

    @Test
    void shouldApplyMagicMobHitChanceFormulaFromGuidance() {
        double hitChance = provider.calculateMagicMobHitChance(80, 50, 55, 20);

        assertEquals(0.625d, hitChance, 1.0e-12d);
    }

    @Test
    void shouldApplySymmetricMobToBotHitChanceFormula() {
        double hitChance = provider.calculateBotAvoidChance(60, 55, 50, 20);

        assertEquals(1.0d, hitChance, 1.0e-12d);
    }

    @Test
    void shouldClampHitChanceBetweenOnePercentAndOneHundredPercent() {
        assertEquals(0.01d, provider.calculatePhysicalMobHitChance(0, 20, 120, 999), 1.0e-12d);
        assertEquals(1.0d, provider.calculatePhysicalMobHitChance(9999, 200, 1, 0), 1.0e-12d);
        assertEquals(0.01d, provider.calculateMagicMobHitChance(0, 20, 120, 999), 1.0e-12d);
        assertEquals(1.0d, provider.calculateMagicMobHitChance(9999, 200, 1, 0), 1.0e-12d);
        assertEquals(0.01d, provider.calculateBotAvoidChance(0, 20, 120, 999), 1.0e-12d);
        assertEquals(1.0d, provider.calculateBotAvoidChance(9999, 120, 20, 0), 1.0e-12d);
    }

    @Test
    void shouldReturnOnlyDamageWhenHitChanceIsGuaranteed() {
        assertTrue(provider.rollDamageLines(8, 10, 10, 1.0d).stream().allMatch(line -> line == 10));
    }

    @Test
    void shouldReturnOnlyMissesWhenHitChanceIsZero() {
        assertTrue(provider.rollDamageLines(8, 10, 10, 0.0d).stream().allMatch(line -> line == 0));
    }

    @Test
    void shouldLetMobAlwaysMissWhenHitChanceIsZero() {
        assertTrue(java.util.stream.IntStream.range(0, 32).allMatch(i -> !provider.doesMobHit(0.0d)));
    }

    @Test
    void shouldLetMobAlwaysHitWhenHitChanceIsOne() {
        assertTrue(java.util.stream.IntStream.range(0, 32).allMatch(i -> provider.doesMobHit(1.0d)));
    }

    @Test
    void shouldScalePhysicalSkillDamageFromBaseWeaponRange() {
        Character bot = mockDamageBot();
        StatEffect effect = mock(StatEffect.class);
        when(bot.getTotalWatk()).thenReturn(100);
        when(bot.calculateMaxBaseDamage(100)).thenReturn(1_000);
        when(bot.calculateMinBaseDamage(100)).thenReturn(500);
        when(effect.getDamage()).thenReturn(260);

        BotCombatFormulaProvider.DamageProfile profile = provider.resolveDamageProfile(bot, 1001005, effect, false);

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

        BotCombatFormulaProvider.DamageProfile profile =
                provider.resolveDamageProfile(bot, constants.skills.Rogue.LUCKY_SEVEN, effect, false);

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

        BotCombatFormulaProvider.DamageProfile profile = provider.resolveDamageProfile(bot, 2101004, effect, true);

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

        BotCombatFormulaProvider.DamageProfile profile = provider.resolveDamageProfile(bot, 0, effect, false);

        assertEquals(777, profile.minDamage());
        assertEquals(777, profile.maxDamage());
        assertTrue(profile.alwaysHit());
    }

    private static Character mockDamageBot() {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.BEGINNER);
        return bot;
    }
}
