package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AgentCombatAmmoPolicyTest {
    @Test
    void shouldClearWarningStateForNonAmmoWeaponsAndAvailableMageMpPots() {
        assertEquals(AgentCombatAmmoPolicy.AmmoCheckDecision.CLEAR_WARNING_STATE,
                AgentCombatAmmoPolicy.ammoCheckDecision(
                        false, false, 0, Integer.MAX_VALUE, 500, false, false));
        assertEquals(AgentCombatAmmoPolicy.AmmoCheckDecision.CLEAR_WARNING_STATE,
                AgentCombatAmmoPolicy.ammoCheckDecision(
                        true, false, 1, Integer.MAX_VALUE, 500, false, false));
    }

    @Test
    void shouldReportMageNoMpPotsOnlyOnceUntilStateClears() {
        assertEquals(AgentCombatAmmoPolicy.AmmoCheckDecision.MAGE_NO_MP_POTS,
                AgentCombatAmmoPolicy.ammoCheckDecision(
                        true, false, 0, Integer.MAX_VALUE, 500, false, false));
        assertEquals(AgentCombatAmmoPolicy.AmmoCheckDecision.NO_CHANGE,
                AgentCombatAmmoPolicy.ammoCheckDecision(
                        true, false, 0, Integer.MAX_VALUE, 500, false, true));
    }

    @Test
    void shouldClearProjectileWarningAtOrAboveThreshold() {
        assertEquals(AgentCombatAmmoPolicy.AmmoCheckDecision.CLEAR_WARNING_STATE,
                AgentCombatAmmoPolicy.ammoCheckDecision(
                        false, true, 0, 500, 500, true, true));
    }

    @Test
    void shouldWarnOnceForLowPositiveProjectileAmmo() {
        assertEquals(AgentCombatAmmoPolicy.AmmoCheckDecision.PROJECTILE_LOW_AMMO,
                AgentCombatAmmoPolicy.ammoCheckDecision(
                        false, true, 0, 1, 500, false, false));
        assertEquals(AgentCombatAmmoPolicy.AmmoCheckDecision.NO_CHANGE,
                AgentCombatAmmoPolicy.ammoCheckDecision(
                        false, true, 0, 1, 500, true, false));
    }

    @Test
    void shouldReportProjectileNoAmmoOnlyOnceUntilStateClears() {
        assertEquals(AgentCombatAmmoPolicy.AmmoCheckDecision.PROJECTILE_NO_AMMO,
                AgentCombatAmmoPolicy.ammoCheckDecision(
                        false, true, 0, 0, 500, false, false));
        assertEquals(AgentCombatAmmoPolicy.AmmoCheckDecision.NO_CHANGE,
                AgentCombatAmmoPolicy.ammoCheckDecision(
                        false, true, 0, 0, 500, false, true));
    }
}
