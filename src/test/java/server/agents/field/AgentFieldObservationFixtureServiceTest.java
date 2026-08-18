package server.agents.field;

import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFieldObservationFixtureServiceTest {
    @Test
    void acceptsOnlyObservationLoadoutSlots() {
        assertEquals("Wp", AgentFieldObservationFixtureService.normalizedSlot("Wp"));
        assertEquals("Wp", AgentFieldObservationFixtureService.normalizedSlot("WpSi"));
        assertEquals("Si", AgentFieldObservationFixtureService.normalizedSlot("Si"));
        assertEquals("Ae", AgentFieldObservationFixtureService.normalizedSlot("Ae"));

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
        assertEquals(-10, AgentFieldObservationFixtureService.equippedSlot("Si"));
        assertEquals(-11, AgentFieldObservationFixtureService.equippedSlot("Wp"));
        assertEquals(0, AgentFieldObservationFixtureService.equippedSlot("Pe"));
    }
}
