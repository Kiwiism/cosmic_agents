package server.agents.field;

import client.Character;
import client.Job;
import client.inventory.WeaponType;
import client.inventory.Equip;
import org.junit.jupiter.api.Test;
import server.combat.CombatFormulaProvider;
import server.agents.integration.InventoryGateway;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentFieldObservationFixtureServiceTest {
    @Test
    void acceptsOnlyObservationLoadoutSlots() {
        assertEquals("Wp", AgentFieldObservationFixtureService.normalizedSlot("Wp"));
        assertEquals("Wp", AgentFieldObservationFixtureService.normalizedSlot("WpSi"));
        assertEquals("Si", AgentFieldObservationFixtureService.normalizedSlot("Si"));
        assertEquals("Ae", AgentFieldObservationFixtureService.normalizedSlot("Ae"));
        assertEquals("Sr", AgentFieldObservationFixtureService.normalizedSlot("Sr"));

        assertEquals("", AgentFieldObservationFixtureService.normalizedSlot("Pe"));
        assertEquals("", AgentFieldObservationFixtureService.normalizedSlot("Sd"));
        assertEquals("", AgentFieldObservationFixtureService.normalizedSlot("Af"));
        assertEquals("", AgentFieldObservationFixtureService.normalizedSlot("Ri"));
        assertEquals("", AgentFieldObservationFixtureService.normalizedSlot(null));
    }

    @Test
    void permitsShieldsOnlyWithOneHandedShieldCapableWeapons() {
        assertTrue(AgentFieldObservationFixtureService.supportsShield(WeaponType.SWORD1H, "Wp"));
        assertTrue(AgentFieldObservationFixtureService.supportsShield(WeaponType.GENERAL1H_SWING, "Wp"));
        assertTrue(AgentFieldObservationFixtureService.supportsShield(WeaponType.DAGGER_THIEVES, "Wp"));
        assertTrue(AgentFieldObservationFixtureService.supportsShield(WeaponType.WAND, "Wp"));
        assertTrue(AgentFieldObservationFixtureService.supportsShield(WeaponType.STAFF, "Wp"));

        assertFalse(AgentFieldObservationFixtureService.supportsShield(WeaponType.SWORD2H, "WpSi"));
        assertFalse(AgentFieldObservationFixtureService.supportsShield(WeaponType.BOW, "WpSi"));
        assertFalse(AgentFieldObservationFixtureService.supportsShield(WeaponType.CLAW, "Wp"));
        assertFalse(AgentFieldObservationFixtureService.supportsShield(WeaponType.GUN, "Wp"));
    }

    @Test
    void mapsEveryObservationVisualSlotToItsEquippedPosition() {
        assertEquals(-1, AgentFieldObservationFixtureService.equippedSlot("Cp"));
        assertEquals(-4, AgentFieldObservationFixtureService.equippedSlot("Ae"));
        assertEquals(-5, AgentFieldObservationFixtureService.equippedSlot("Ma"));
        assertEquals(-5, AgentFieldObservationFixtureService.equippedSlot("MaPn"));
        assertEquals(-6, AgentFieldObservationFixtureService.equippedSlot("Pn"));
        assertEquals(-7, AgentFieldObservationFixtureService.equippedSlot("So"));
        assertEquals(-8, AgentFieldObservationFixtureService.equippedSlot("Gv"));
        assertEquals(-9, AgentFieldObservationFixtureService.equippedSlot("Sr"));
        assertEquals(-10, AgentFieldObservationFixtureService.equippedSlot("Si"));
        assertEquals(-11, AgentFieldObservationFixtureService.equippedSlot("Wp"));
        assertEquals(0, AgentFieldObservationFixtureService.equippedSlot("Pe"));
    }

    @Test
    void appliesEveryGuaranteedScrollEffectAndConsumesEverySlot() {
        Equip weapon = Equip.restored(1_302_039, (short) -11);
        weapon.setUpgradeSlots(7);
        weapon.setWatk((short) 77);

        AgentFieldObservationFixtureService.applySuccessfulScrollEffects(
                weapon, Map.of("PAD", 2, "STR", 1, "success", 60), 7);

        assertEquals(91, weapon.getWatk());
        assertEquals(7, weapon.getStr());
        assertEquals(0, weapon.getUpgradeSlots());
        assertEquals(7, weapon.getLevel());
    }

    @Test
    void appliesFiveTenPercentShoeAndCapeSuccesses() {
        Equip shoes = Equip.restored(1_072_127, (short) -7);
        shoes.setUpgradeSlots(5);
        Equip cape = Equip.restored(1_102_055, (short) -9);
        cape.setUpgradeSlots(5);

        AgentFieldObservationFixtureService.applySuccessfulScrollEffects(
                shoes, Map.of("Speed", 1, "Jump", 5, "success", 100), 5);
        AgentFieldObservationFixtureService.applySuccessfulScrollEffects(
                cape, Map.of("STR", 3, "success", 10), 5);

        assertEquals(5, shoes.getSpeed());
        assertEquals(25, shoes.getJump());
        assertEquals(15, cape.getStr());
        assertEquals(0, shoes.getUpgradeSlots());
        assertEquals(0, cape.getUpgradeSlots());
        assertEquals(5, shoes.getLevel());
        assertEquals(5, cape.getLevel());
    }

    @Test
    void choosesWeaponPowerAfterAccuracyInsteadOfLowestItemId() {
        InventoryGateway inventory = mock(InventoryGateway.class);
        Equip starter = mock(Equip.class);
        Equip levelTwentyFive = mock(Equip.class);
        when(starter.getAcc()).thenReturn((short) 0);
        when(starter.getWatk()).thenReturn((short) 18);
        when(levelTwentyFive.getAcc()).thenReturn((short) 0);
        when(levelTwentyFive.getWatk()).thenReturn((short) 30);
        when(inventory.getEquipById(1_482_000)).thenReturn(starter);
        when(inventory.getEquipById(1_482_003)).thenReturn(levelTwentyFive);

        List<Integer> sorted = List.of(1_482_000, 1_482_003).stream()
                .sorted(AgentFieldObservationFixtureService.candidateWeaponComparator(inventory))
                .toList();

        assertEquals(List.of(1_482_003, 1_482_000), sorted);
    }

    @Test
    void permitsAccuracySpearOnlyForKpqKnuckleFixture() {
        Character pirate = mock(Character.class);
        when(pirate.getJob()).thenReturn(Job.PIRATE);

        assertTrue(AgentFieldObservationEquipmentRepository.itemIds().contains(1_432_008));
        assertTrue(AgentFieldObservationFixtureService.weaponCompatible(
                pirate, WeaponType.SPEAR_STAB, "pirate-knuckle"));
        assertTrue(AgentFieldObservationFixtureService.weaponCompatible(
                pirate, WeaponType.SPEAR_SWING, "pirate-knuckle"));
        assertFalse(AgentFieldObservationFixtureService.weaponCompatible(
                pirate, WeaponType.POLE_ARM_SWING, "pirate-knuckle"));
        assertFalse(AgentFieldObservationFixtureService.weaponCompatible(
                pirate, WeaponType.SPEAR_STAB, "pirate-gun"));
    }

    @Test
    void kpqWarriorUsesFishSpearAndAccuracyHeadbandAboveFallbackHitRate() {
        assertEquals(1_002_014,
                AgentFieldObservationFixtureService.kpqPreferredEquipmentId("warrior", "Cp"));
        assertEquals(1_432_008,
                AgentFieldObservationFixtureService.kpqPreferredEquipmentId("warrior", "Wp"));
        assertEquals(1_432_008,
                AgentFieldObservationFixtureService.kpqPreferredEquipmentId("pirate-knuckle", "Wp"));

        double hitChance = CombatFormulaProvider.getInstance()
                .calculatePhysicalMobHitChance(33, 25, 32, 10);
        assertTrue(hitChance >= 0.50d);
        assertTrue(hitChance < 0.60d);
    }

    @Test
    void fixtureShieldsAreCareerAwareAndNeverUseMapleShield() {
        assertFalse(AgentFieldObservationFixtureService.shieldAllowedForCareer(1_092_030, "warrior"));
        assertFalse(AgentFieldObservationFixtureService.shieldAllowedForCareer(1_092_030, "magician"));
        assertTrue(AgentFieldObservationFixtureService.shieldAllowedForCareer(1_092_003, "magician"));
        assertTrue(AgentFieldObservationFixtureService.shieldAllowedForCareer(1_092_008, "thief-dagger"));
        assertTrue(AgentFieldObservationFixtureService.shieldAllowedForCareer(1_092_018, "thief-dagger"));
        assertFalse(AgentFieldObservationFixtureService.shieldAllowedForCareer(1_092_018, "magician"));
        assertTrue(AgentFieldObservationFixtureService.shieldAllowedForCareer(1_092_001, "warrior"));
        assertFalse(AgentFieldObservationFixtureService.shieldAllowedForCareer(1_092_001, "thief-dagger"));
    }
}
