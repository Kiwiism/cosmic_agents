package server.agents.capabilities.combat;

import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCombatAmmoCounterTest {
    @Test
    void identifiesWeaponsThatRequireProjectileAmmo() {
        assertTrue(AgentCombatAmmoCounter.isRangedAmmoWeapon(WeaponType.BOW));
        assertTrue(AgentCombatAmmoCounter.isRangedAmmoWeapon(WeaponType.CROSSBOW));
        assertTrue(AgentCombatAmmoCounter.isRangedAmmoWeapon(WeaponType.CLAW));
        assertTrue(AgentCombatAmmoCounter.isRangedAmmoWeapon(WeaponType.GUN));

        assertFalse(AgentCombatAmmoCounter.isRangedAmmoWeapon(WeaponType.DAGGER_THIEVES));
        assertFalse(AgentCombatAmmoCounter.isRangedAmmoWeapon(WeaponType.WAND));
        assertFalse(AgentCombatAmmoCounter.isRangedAmmoWeapon(null));
    }

    @Test
    void nonAmmoWeaponsDoNotRequireInventoryInspection() {
        assertEquals(Integer.MAX_VALUE, AgentCombatAmmoCounter.countAmmo(null, null));
        assertEquals(Integer.MAX_VALUE, AgentCombatAmmoCounter.countAmmo(null, WeaponType.DAGGER_THIEVES));
        assertEquals(Integer.MAX_VALUE, AgentCombatAmmoCounter.countAmmo(null, WeaponType.WAND));
    }
}
