package server.agents.field;

import config.AgentTuning;

/** Tunable field-coordination cadence and lease limits. */
public final class AgentFieldPolicyConfig {
    private static final String TUNING_PREFIX = "server.agents.field.AgentFieldPolicyConfig.";

    private AgentFieldPolicyConfig() {
    }

    public static long assignmentLeaseMs() {
        return tuningLong("ASSIGNMENT_LEASE_MS");
    }

    public static long refreshIntervalMs() {
        return tuningLong("REFRESH_INTERVAL_MS");
    }

    public static long emptyPlatformReleaseMs() {
        return tuningLong("EMPTY_PLATFORM_RELEASE_MS");
    }

    public static long rebalanceIntervalMs() {
        return tuningLong("REBALANCE_INTERVAL_MS");
    }

    public static long staleSessionMs() {
        return tuningLong("STALE_SESSION_MS");
    }

    public static int maximumParticipants() {
        return tuningInt("MAXIMUM_PARTICIPANTS");
    }

    public static int maximumObservationParticipants() {
        return tuningInt("MAXIMUM_OBSERVATION_PARTICIPANTS");
    }

    public static long graphWarmupRetryMs() {
        return tuningLong("GRAPH_WARMUP_RETRY_MS");
    }

    public static int maximumGraphWarmupAttempts() {
        return tuningInt("MAXIMUM_GRAPH_WARMUP_ATTEMPTS");
    }

    public static int safeSpotSpawnClearancePx() {
        return tuningInt("SAFE_SPOT_SPAWN_CLEARANCE_PX");
    }

    public static int safeSpotSampleStepPx() {
        return tuningInt("SAFE_SPOT_SAMPLE_STEP_PX");
    }

    public static int testObjectiveKillsPerMob() {
        return tuningInt("TEST_OBJECTIVE_KILLS_PER_MOB");
    }

    public static long objectivePopulationWeight() {
        return tuningLong("OBJECTIVE_POPULATION_WEIGHT");
    }

    public static long objectiveCoverageWeight() {
        return tuningLong("OBJECTIVE_COVERAGE_WEIGHT");
    }

    public static long retainedSeedBonus() {
        return tuningLong("RETAINED_SEED_BONUS");
    }

    public static long sharedPlatformPenalty() {
        return tuningLong("SHARED_PLATFORM_PENALTY");
    }

    public static long playerProximityPenalty() {
        return tuningLong("PLAYER_PROXIMITY_PENALTY");
    }

    public static long capabilityDensityWeight() {
        return tuningLong("CAPABILITY_DENSITY_WEIGHT");
    }

    public static long capabilityRangeWeight() {
        return tuningLong("CAPABILITY_RANGE_WEIGHT");
    }

    public static long capabilityMobilityWeight() {
        return tuningLong("CAPABILITY_MOBILITY_WEIGHT");
    }

    public static long capabilitySupportWeight() {
        return tuningLong("CAPABILITY_SUPPORT_WEIGHT");
    }

    public static long roamerDeadEndPenalty() {
        return tuningLong("ROAMER_DEAD_END_PENALTY");
    }

    public static long reservePopulationPenalty() {
        return tuningLong("RESERVE_POPULATION_PENALTY");
    }

    public static long safeSpotPopulationWeight() {
        return tuningLong("SAFE_SPOT_POPULATION_WEIGHT");
    }

    public static long safeSpotDeadEndPenalty() {
        return tuningLong("SAFE_SPOT_DEAD_END_PENALTY");
    }

    private static int tuningInt(String suffix) {
        return AgentTuning.intValue(TUNING_PREFIX + suffix);
    }

    private static long tuningLong(String suffix) {
        return AgentTuning.longValue(TUNING_PREFIX + suffix);
    }

}
