package server.agents.capabilities.supplies;

import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.supplies.AgentAmmoSharePolicy.DonorScore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAmmoSharePolicyTest {
    @Test
    void shouldOnlyRequestShareForBowAndCrossbowAmmo() {
        assertTrue(AgentAmmoSharePolicy.canRequestShare(WeaponType.BOW));
        assertTrue(AgentAmmoSharePolicy.canRequestShare(WeaponType.CROSSBOW));

        assertFalse(AgentAmmoSharePolicy.canRequestShare(WeaponType.CLAW));
        assertFalse(AgentAmmoSharePolicy.canRequestShare(WeaponType.GUN));
        assertFalse(AgentAmmoSharePolicy.canRequestShare(WeaponType.SWORD1H));
        assertFalse(AgentAmmoSharePolicy.canRequestShare(WeaponType.NOT_A_WEAPON));
    }

    @Test
    void shouldDonateHalfExcessWhenDonorNeedsSameAmmoOtherwiseAllMatchingAmmo() {
        assertEquals(100, AgentAmmoSharePolicy.donationQuantity(700, 500, true));
        assertEquals(0, AgentAmmoSharePolicy.donationQuantity(501, 500, true));
        assertEquals(700, AgentAmmoSharePolicy.donationQuantity(700, 500, false));
    }

    @Test
    void shouldPreferNonSameAmmoDonorsThenHighestAmmoCount() {
        DonorScore sameAmmoHigh = new DonorScore(true, 1000);
        DonorScore differentAmmoLow = new DonorScore(false, 200);
        DonorScore differentAmmoHigh = new DonorScore(false, 300);

        assertTrue(AgentAmmoSharePolicy.isBetterDonor(sameAmmoHigh, null));
        assertTrue(AgentAmmoSharePolicy.isBetterDonor(differentAmmoLow, sameAmmoHigh));
        assertFalse(AgentAmmoSharePolicy.isBetterDonor(sameAmmoHigh, differentAmmoLow));
        assertTrue(AgentAmmoSharePolicy.isBetterDonor(differentAmmoHigh, differentAmmoLow));
        assertFalse(AgentAmmoSharePolicy.isBetterDonor(differentAmmoLow, differentAmmoHigh));
    }
}
