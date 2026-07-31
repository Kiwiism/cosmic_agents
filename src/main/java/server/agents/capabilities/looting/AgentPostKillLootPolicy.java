package server.agents.capabilities.looting;

import client.inventory.WeaponType;
import server.agents.capabilities.combat.AgentCombatAmmoCounter;

/** Pure timing policy for interrupting combat to collect recent drops. */
public final class AgentPostKillLootPolicy {
    private AgentPostKillLootPolicy() {
    }

    public static boolean shouldCollect(WeaponType weaponType,
                                        AgentPostKillLootState.Snapshot state,
                                        boolean hasCombatTarget,
                                        long nowMs) {
        if (state == null || !state.hasKills()) {
            return !hasCombatTarget;
        }
        if (!isRanged(weaponType)) {
            return true;
        }
        return !hasCombatTarget
                || state.killCount() >= AgentLootCollectionPolicyConfig.rangedBatchKills()
                || nowMs - state.oldestKillAtMs()
                >= AgentLootCollectionPolicyConfig.rangedBatchMaxWaitMs();
    }

    static long targetLootAgeMs(WeaponType weaponType, boolean recentKillDrop) {
        return recentKillDrop && !isRanged(weaponType)
                ? AgentLootCollectionPolicyConfig.meleeRecentKillTargetAgeMs()
                : AgentLootEligibility.MIN_TARGET_LOOT_AGE_MS;
    }

    static boolean isRanged(WeaponType weaponType) {
        return AgentCombatAmmoCounter.isRangedAmmoWeapon(weaponType)
                || weaponType == WeaponType.WAND
                || weaponType == WeaponType.STAFF;
    }
}
