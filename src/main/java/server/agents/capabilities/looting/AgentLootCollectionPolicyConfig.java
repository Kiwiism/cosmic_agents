package server.agents.capabilities.looting;

import config.AgentTuning;

/** Tuneable bounds for post-kill collection without changing pickup eligibility. */
public final class AgentLootCollectionPolicyConfig {
    private static final String TUNING_PREFIX =
            "server.agents.capabilities.looting.AgentLootCollectionPolicyConfig.";

    private AgentLootCollectionPolicyConfig() {
    }

    public static int meleeImmediateRadius() {
        return tuningInt("MELEE_IMMEDIATE_RADIUS");
    }

    public static long meleeRecentKillTargetAgeMs() {
        return tuningLong("MELEE_RECENT_KILL_TARGET_AGE_MS");
    }

    public static int rangedBatchKills() {
        return tuningInt("RANGED_BATCH_KILLS");
    }

    public static long rangedBatchMaxWaitMs() {
        return tuningLong("RANGED_BATCH_MAX_WAIT_MS");
    }

    public static int maxTrackedKills() {
        return tuningInt("MAX_TRACKED_KILLS");
    }

    public static long trackedKillLifetimeMs() {
        return tuningLong("TRACKED_KILL_LIFETIME_MS");
    }

    public static long preExitMaxWaitMs() {
        return tuningLong("PRE_EXIT_MAX_WAIT_MS");
    }

    public static int preExitLootRadius() {
        return tuningInt("PRE_EXIT_LOOT_RADIUS");
    }

    private static int tuningInt(String suffix) {
        return AgentTuning.intValue(TUNING_PREFIX + suffix);
    }

    private static long tuningLong(String suffix) {
        return AgentTuning.longValue(TUNING_PREFIX + suffix);
    }
}
