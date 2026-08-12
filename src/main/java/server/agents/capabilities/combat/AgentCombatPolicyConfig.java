package server.agents.capabilities.combat;

import config.AgentTuning;

/** Tunable policy limits; attack packet construction remains outside this layer. */
public final class AgentCombatPolicyConfig {
    private static final String TUNING_PREFIX =
            "server.agents.capabilities.combat.AgentCombatPolicyConfig.";

    private AgentCombatPolicyConfig() {
    }

    public static int maxConsecutiveIncidentalKills() {
        return tuningInt("MAX_CONSECUTIVE_INCIDENTAL_KILLS");
    }

    public static long localTargetLeaseMs() {
        return tuningLong("LOCAL_TARGET_LEASE_MS");
    }

    public static int localTargetLeaseKills() {
        return tuningInt("LOCAL_TARGET_LEASE_KILLS");
    }

    public static int localTargetLeaseEmptyScans() {
        return tuningInt("LOCAL_TARGET_LEASE_EMPTY_SCANS");
    }

    public static int spawnPressureMinTargetSharePercent() {
        return tuningInt("SPAWN_PRESSURE_MIN_TARGET_SHARE_PERCENT");
    }

    public static int maxIncidentalKillsPerPlatformLease() {
        return tuningInt("MAX_INCIDENTAL_KILLS_PER_PLATFORM_LEASE");
    }

    public static long platformLeaseMs() {
        return tuningLong("PLATFORM_LEASE_MS");
    }

    public static int routeBlockerCorridorWidth() {
        return tuningInt("ROUTE_BLOCKER_CORRIDOR_WIDTH");
    }

    public static long routeBlockerTimeoutMs() {
        return tuningLong("ROUTE_BLOCKER_TIMEOUT_MS");
    }

    public static int routeBlockerMaxKills() {
        return tuningInt("ROUTE_BLOCKER_MAX_KILLS");
    }

    public static long routeBlockerTravelCooldownMs() {
        return tuningLong("ROUTE_BLOCKER_TRAVEL_COOLDOWN_MS");
    }

    public static int aoeClusterRadiusPx() {
        return tuningInt("AOE_CLUSTER_RADIUS_PX");
    }

    public static long aoeClusterBonusPerMob() {
        return tuningLong("AOE_CLUSTER_BONUS_PER_MOB");
    }

    public static long localTravelVerticalCostPerPx() {
        return tuningLong("LOCAL_TRAVEL_VERTICAL_COST_PER_PX");
    }

    public static long localTargetVerticalWeight() {
        return tuningLong("LOCAL_TARGET_VERTICAL_WEIGHT");
    }

    public static long localTargetOffLevelPenalty() {
        return tuningLong("LOCAL_TARGET_OFF_LEVEL_PENALTY");
    }

    public static long localTargetOtherFootholdPenalty() {
        return tuningLong("LOCAL_TARGET_OTHER_FOOTHOLD_PENALTY");
    }

    public static int upwardPlatformTolerancePx() {
        return tuningInt("UPWARD_PLATFORM_TOLERANCE_PX");
    }

    public static long upwardPlatformBasePenalty() {
        return tuningLong("UPWARD_PLATFORM_BASE_PENALTY");
    }

    public static long upwardPlatformPenaltyPerPx() {
        return tuningLong("UPWARD_PLATFORM_PENALTY_PER_PX");
    }

    public static long minimumSingleTargetScore() {
        return tuningLong("MINIMUM_SINGLE_TARGET_SCORE");
    }

    private static int tuningInt(String suffix) {
        return AgentTuning.intValue(TUNING_PREFIX + suffix);
    }

    private static long tuningLong(String suffix) {
        return AgentTuning.longValue(TUNING_PREFIX + suffix);
    }

    private static boolean tuningBoolean(String suffix) {
        return AgentTuning.booleanValue(TUNING_PREFIX + suffix);
    }
}
