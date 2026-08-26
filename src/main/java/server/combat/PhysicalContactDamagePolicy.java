package server.combat;

/** Shared server authority for buffs that negate monster-contact damage. */
public final class PhysicalContactDamagePolicy {
    private PhysicalContactDamagePolicy() {
    }

    public static boolean isNegated(int damageFrom, boolean darkSightActive) {
        return darkSightActive && damageFrom == -1;
    }
}
