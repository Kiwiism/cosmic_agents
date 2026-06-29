package server.agents.capabilities.supplies;

import client.inventory.WeaponType;

public final class AgentAmmoSharePolicy {
    private AgentAmmoSharePolicy() {
    }

    public record DonorScore(boolean donorNeedsSameAmmo, int matchingAmmoCount) {
    }

    public static boolean canRequestShare(WeaponType weaponType) {
        return weaponType == WeaponType.BOW || weaponType == WeaponType.CROSSBOW;
    }

    public static int donationQuantity(int matchingAmmoCount, int lowWarn, boolean donorNeedsSameAmmo) {
        return donorNeedsSameAmmo ? (matchingAmmoCount - lowWarn) / 2 : matchingAmmoCount;
    }

    public static boolean isBetterDonor(DonorScore candidate, DonorScore best) {
        if (best == null) {
            return true;
        }
        if (candidate.donorNeedsSameAmmo() != best.donorNeedsSameAmmo()) {
            return !candidate.donorNeedsSameAmmo();
        }
        return candidate.matchingAmmoCount() > best.matchingAmmoCount();
    }
}
