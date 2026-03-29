package server.bots;

import client.BuffStat;
import client.Character;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        double hitChance = provider.calculateMobHitChance(36, 50, 55, 20);

        assertEquals(0.8035714285714286d, hitChance, 1.0e-12d);
    }

    @Test
    void shouldApplySymmetricMobToBotHitChanceFormula() {
        double hitChance = provider.calculateBotAvoidChance(60, 55, 50, 20);

        assertEquals(1.0d, hitChance, 1.0e-12d);
    }

    @Test
    void shouldClampHitChanceBetweenOnePercentAndOneHundredPercent() {
        assertEquals(0.01d, provider.calculateMobHitChance(0, 20, 120, 999), 1.0e-12d);
        assertEquals(1.0d, provider.calculateMobHitChance(9999, 200, 1, 0), 1.0e-12d);
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
}
