package server.agents.progression;

/** Pure deterministic scoring used by quest, grind-map, and hunt-map selection. */
final class AgentProgressionDecisionPolicy {
    private static final String TUNING_PREFIX =
            "server.agents.progression.AgentProgressionDecisionPolicy.";
    private static final int MIN_QUEST_DECISION_PERCENT = tuningInt("MIN_QUEST_DECISION_PERCENT");
    private static final int MAX_QUEST_DECISION_PERCENT = tuningInt("MAX_QUEST_DECISION_PERCENT");
    private static final int PERCENT_MAX = tuningInt("PERCENT_MAX");
    private static final int PROFILE_PERCENT_CENTER = tuningInt("PROFILE_PERCENT_CENTER");
    private static final int QUEST_LEVEL_FIT_BASE = tuningInt("QUEST_LEVEL_FIT_BASE");
    private static final int QUEST_LEVEL_FIT_MULTIPLIER = tuningInt("QUEST_LEVEL_FIT_MULTIPLIER");
    private static final int QUEST_OBJECTIVE_BURDEN_MULTIPLIER =
            tuningInt("QUEST_OBJECTIVE_BURDEN_MULTIPLIER");
    private static final int QUEST_LOCAL_ROUTINE_MULTIPLIER =
            tuningInt("QUEST_LOCAL_ROUTINE_MULTIPLIER");
    private static final int QUEST_TRAVEL_MULTIPLIER = tuningInt("QUEST_TRAVEL_MULTIPLIER");
    private static final int TRAINING_LEVEL_FIT_BASE = tuningInt("TRAINING_LEVEL_FIT_BASE");
    private static final int TRAINING_LEVEL_DISTANCE_PENALTY =
            tuningInt("TRAINING_LEVEL_DISTANCE_PENALTY");
    private static final int TRAINING_CHOICE_WEIGHT_MULTIPLIER =
            tuningInt("TRAINING_CHOICE_WEIGHT_MULTIPLIER");
    private static final int TRAINING_LEVEL_FIT_MULTIPLIER =
            tuningInt("TRAINING_LEVEL_FIT_MULTIPLIER");
    private static final int TRAINING_OCCUPANCY_PENALTY_MULTIPLIER =
            tuningInt("TRAINING_OCCUPANCY_PENALTY_MULTIPLIER");
    private static final int TRAINING_RISK_PENALTY_MULTIPLIER =
            tuningInt("TRAINING_RISK_PENALTY_MULTIPLIER");
    private static final int TRAINING_LOCAL_ROUTINE_MULTIPLIER =
            tuningInt("TRAINING_LOCAL_ROUTINE_MULTIPLIER");
    private static final int TRAINING_TRAVEL_MULTIPLIER =
            tuningInt("TRAINING_TRAVEL_MULTIPLIER");
    private static final int TRAINING_EXPLORATION_MULTIPLIER =
            tuningInt("TRAINING_EXPLORATION_MULTIPLIER");
    private static final int HUNT_RANK_BASE = tuningInt("HUNT_RANK_BASE");
    private static final int HUNT_EFFICIENCY_MULTIPLIER =
            tuningInt("HUNT_EFFICIENCY_MULTIPLIER");
    private static final int HUNT_OCCUPANCY_PENALTY_MULTIPLIER =
            tuningInt("HUNT_OCCUPANCY_PENALTY_MULTIPLIER");
    private static final int HUNT_LOCAL_ROUTINE_MULTIPLIER =
            tuningInt("HUNT_LOCAL_ROUTINE_MULTIPLIER");
    private static final int HUNT_TRAVEL_MULTIPLIER = tuningInt("HUNT_TRAVEL_MULTIPLIER");

    private AgentProgressionDecisionPolicy() {
    }

    static int questDecisionPercent(AgentProgressionProfile profile) {
        int total = profile.questPreference() + profile.grindPreference();
        return Math.max(MIN_QUEST_DECISION_PERCENT, Math.min(MAX_QUEST_DECISION_PERCENT,
                Math.round(profile.questPreference() * (float) PERCENT_MAX / total)));
    }

    static int questDecisionPercent(AgentProgressionProfile profile, int globalBaselinePercent) {
        int profilePreference = questDecisionPercent(profile);
        return Math.max(
                0,
                Math.min(
                        PERCENT_MAX,
                        globalBaselinePercent + profilePreference - PROFILE_PERCENT_CENTER));
    }

    static long questScore(AgentProgressionProfile profile,
                           int characterId,
                           int level,
                           int currentMapId,
                           AgentVictoriaQuestRuntimeCatalog.Entry quest) {
        int minLevel = quest.minLevel() == null ? 1 : quest.minLevel();
        int levelFit = Math.max(0, QUEST_LEVEL_FIT_BASE - Math.abs(level - minLevel));
        int objectiveBurden = quest.huntingObjectives().size();
        boolean local = quest.startMapIds().contains(currentMapId);
        long score = (long) levelFit * profile.efficiencyPreference()
                * QUEST_LEVEL_FIT_MULTIPLIER;
        score -= (long) objectiveBurden * profile.efficiencyPreference()
                * QUEST_OBJECTIVE_BURDEN_MULTIPLIER;
        score += local
                ? (long) profile.routinePreference() * QUEST_LOCAL_ROUTINE_MULTIPLIER
                : (long) profile.travelTolerance() * QUEST_TRAVEL_MULTIPLIER;
        score += (long) deterministicVariation(characterId, level, quest.questId())
                * profile.explorationPreference();
        return score;
    }

    static long trainingMapScore(AgentProgressionProfile profile,
                                 int characterId,
                                 int level,
                                 int currentMapId,
                                 AgentVictoriaTrainingCatalog.TrainingChoice choice,
                                 AgentVictoriaTrainingCatalog.TrainingMap map,
                                 int occupancy) {
        int levelFit = level >= map.recommendedMinLevel() && level <= map.recommendedMaxLevel()
                ? TRAINING_LEVEL_FIT_BASE
                : Math.max(0, TRAINING_LEVEL_FIT_BASE - distanceToRange(level,
                map.recommendedMinLevel(), map.recommendedMaxLevel())
                * TRAINING_LEVEL_DISTANCE_PENALTY);
        int occupancyRatio = Math.min(
                PERCENT_MAX,
                occupancy * PERCENT_MAX / Math.max(1, map.maximumAgents()));
        int averageMobLevel = map.spawns().stream()
                .filter(spawn -> !"hazard".equalsIgnoreCase(spawn.role()))
                .mapToInt(AgentVictoriaTrainingCatalog.SpawnGroup::mobLevel)
                .sum() / Math.max(1, (int) map.spawns().stream()
                .filter(spawn -> !"hazard".equalsIgnoreCase(spawn.role())).count());
        int riskGap = Math.max(0, averageMobLevel - level);
        long score = (long) choice.weight() * profile.efficiencyPreference()
                * TRAINING_CHOICE_WEIGHT_MULTIPLIER;
        score += (long) levelFit * profile.efficiencyPreference()
                * TRAINING_LEVEL_FIT_MULTIPLIER;
        score -= (long) occupancyRatio * profile.crowdAvoidance()
                * TRAINING_OCCUPANCY_PENALTY_MULTIPLIER;
        score -= (long) riskGap * (PERCENT_MAX - profile.riskTolerance())
                * TRAINING_RISK_PENALTY_MULTIPLIER;
        score += map.mapId() == currentMapId
                ? (long) profile.routinePreference() * TRAINING_LOCAL_ROUTINE_MULTIPLIER
                : (long) profile.travelTolerance() * TRAINING_TRAVEL_MULTIPLIER;
        score += (long) deterministicVariation(characterId, level, map.mapId())
                * profile.explorationPreference() * TRAINING_EXPLORATION_MULTIPLIER;
        return score;
    }

    static long huntMapScore(AgentProgressionProfile profile,
                             int characterId,
                             int level,
                             int currentMapId,
                             AgentVictoriaQuestRuntimeCatalog.HuntMap map,
                             int occupancy) {
        int occupancyRatio = Math.min(
                PERCENT_MAX,
                occupancy * PERCENT_MAX / Math.max(1, map.maximumAgents()));
        long score = (long) Math.max(1, HUNT_RANK_BASE - map.rank())
                * profile.efficiencyPreference() * HUNT_EFFICIENCY_MULTIPLIER;
        score -= (long) occupancyRatio * profile.crowdAvoidance()
                * HUNT_OCCUPANCY_PENALTY_MULTIPLIER;
        score += map.mapId() == currentMapId
                ? (long) profile.routinePreference() * HUNT_LOCAL_ROUTINE_MULTIPLIER
                : (long) profile.travelTolerance() * HUNT_TRAVEL_MULTIPLIER;
        score += (long) deterministicVariation(characterId, level, map.mapId())
                * profile.explorationPreference();
        return score;
    }

    private static int distanceToRange(int value, int minimum, int maximum) {
        if (value < minimum) {
            return minimum - value;
        }
        return value > maximum ? value - maximum : 0;
    }

    private static int deterministicVariation(int characterId, int level, int contentId) {
        return Math.floorMod(characterId * 31 + level * 17 + contentId * 13, 101);
    }

    private static int tuningInt(String name) {
        return config.AgentTuning.intValue(TUNING_PREFIX + name);
    }
}
