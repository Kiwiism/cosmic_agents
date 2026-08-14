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

    public static long staleSessionMs() {
        return tuningLong("STALE_SESSION_MS");
    }

    public static int maximumParticipants() {
        return tuningInt("MAXIMUM_PARTICIPANTS");
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

    public static long adjacencyBonus() {
        return tuningLong("ADJACENCY_BONUS");
    }

    public static long territorySizePenalty() {
        return tuningLong("TERRITORY_SIZE_PENALTY");
    }

    public static long playerProximityPenalty() {
        return tuningLong("PLAYER_PROXIMITY_PENALTY");
    }

    private static int tuningInt(String suffix) {
        return AgentTuning.intValue(TUNING_PREFIX + suffix);
    }

    private static long tuningLong(String suffix) {
        return AgentTuning.longValue(TUNING_PREFIX + suffix);
    }

}
