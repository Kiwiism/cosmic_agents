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

    public static boolean questLocalClearEnforced() {
        return tuningBoolean("QUEST_LOCAL_CLEAR_ENFORCED");
    }

    public static boolean questLocalClearShadowEnabled() {
        return tuningBoolean("QUEST_LOCAL_CLEAR_SHADOW_ENABLED");
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
