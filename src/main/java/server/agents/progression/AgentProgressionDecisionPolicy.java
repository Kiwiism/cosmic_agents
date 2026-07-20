package server.agents.progression;

/** Pure deterministic scoring used by quest, grind-map, and hunt-map selection. */
final class AgentProgressionDecisionPolicy {
    private AgentProgressionDecisionPolicy() {
    }

    static int questDecisionPercent(AgentProgressionProfile profile) {
        int total = profile.questPreference() + profile.grindPreference();
        return Math.max(5, Math.min(95,
                Math.round(profile.questPreference() * 100.0f / total)));
    }

    static int questDecisionPercent(AgentProgressionProfile profile, int globalBaselinePercent) {
        int profilePreference = questDecisionPercent(profile);
        return Math.max(0, Math.min(100, globalBaselinePercent + profilePreference - 50));
    }

    static long questScore(AgentProgressionProfile profile,
                           int characterId,
                           int level,
                           int currentMapId,
                           AgentVictoriaQuestRuntimeCatalog.Entry quest) {
        int minLevel = quest.minLevel() == null ? 1 : quest.minLevel();
        int levelFit = Math.max(0, 20 - Math.abs(level - minLevel));
        int objectiveBurden = quest.huntingObjectives().size();
        boolean local = quest.startMapIds().contains(currentMapId);
        long score = (long) levelFit * profile.efficiencyPreference() * 4L;
        score -= (long) objectiveBurden * profile.efficiencyPreference() * 10L;
        score += local ? (long) profile.routinePreference() * 18L
                : (long) profile.travelTolerance() * 7L;
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
                ? 25 : Math.max(0, 25 - distanceToRange(level,
                map.recommendedMinLevel(), map.recommendedMaxLevel()) * 5);
        int occupancyRatio = Math.min(100, occupancy * 100 / Math.max(1, map.maximumAgents()));
        int averageMobLevel = map.spawns().stream()
                .filter(spawn -> !"hazard".equalsIgnoreCase(spawn.role()))
                .mapToInt(AgentVictoriaTrainingCatalog.SpawnGroup::mobLevel)
                .sum() / Math.max(1, (int) map.spawns().stream()
                .filter(spawn -> !"hazard".equalsIgnoreCase(spawn.role())).count());
        int riskGap = Math.max(0, averageMobLevel - level);
        long score = (long) choice.weight() * profile.efficiencyPreference() * 4L;
        score += (long) levelFit * profile.efficiencyPreference() * 6L;
        score -= (long) occupancyRatio * profile.crowdAvoidance() * 3L;
        score -= (long) riskGap * (100 - profile.riskTolerance()) * 18L;
        score += map.mapId() == currentMapId
                ? (long) profile.routinePreference() * 30L
                : (long) profile.travelTolerance() * 4L;
        score += (long) deterministicVariation(characterId, level, map.mapId())
                * profile.explorationPreference() * 2L;
        return score;
    }

    static long huntMapScore(AgentProgressionProfile profile,
                             int characterId,
                             int level,
                             int currentMapId,
                             AgentVictoriaQuestRuntimeCatalog.HuntMap map,
                             int occupancy) {
        int occupancyRatio = Math.min(100, occupancy * 100 / Math.max(1, map.maximumAgents()));
        long score = (long) Math.max(1, 20 - map.rank())
                * profile.efficiencyPreference() * 40L;
        score -= (long) occupancyRatio * profile.crowdAvoidance() * 4L;
        score += map.mapId() == currentMapId
                ? (long) profile.routinePreference() * 25L
                : (long) profile.travelTolerance() * 5L;
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
}
