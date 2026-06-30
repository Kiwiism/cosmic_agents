package server.agents.capabilities.combat;

public final class AgentCombatAmmoPolicy {
    public enum AmmoCheckDecision {
        CLEAR_WARNING_STATE,
        MAGE_NO_MP_POTS,
        PROJECTILE_LOW_AMMO,
        PROJECTILE_NO_AMMO,
        NO_CHANGE
    }

    private AgentCombatAmmoPolicy() {
    }

    public static AmmoCheckDecision ammoCheckDecision(boolean mage,
                                                      boolean rangedAmmoWeapon,
                                                      int mpPotionCount,
                                                      int ammo,
                                                      int lowWarnThreshold,
                                                      boolean ammoWarnSent,
                                                      boolean noAmmo) {
        if (!mage && !rangedAmmoWeapon) {
            return AmmoCheckDecision.CLEAR_WARNING_STATE;
        }

        if (mage) {
            if (mpPotionCount > 0) {
                return AmmoCheckDecision.CLEAR_WARNING_STATE;
            }
            return noAmmo ? AmmoCheckDecision.NO_CHANGE : AmmoCheckDecision.MAGE_NO_MP_POTS;
        }

        if (ammo >= lowWarnThreshold) {
            return AmmoCheckDecision.CLEAR_WARNING_STATE;
        }
        if (ammo > 0 && !ammoWarnSent) {
            return AmmoCheckDecision.PROJECTILE_LOW_AMMO;
        }
        if (ammo <= 0 && !noAmmo) {
            return AmmoCheckDecision.PROJECTILE_NO_AMMO;
        }
        return AmmoCheckDecision.NO_CHANGE;
    }
}
