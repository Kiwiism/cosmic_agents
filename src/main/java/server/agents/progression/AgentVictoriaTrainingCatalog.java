package server.agents.progression;

import java.util.List;

/**
 * Versioned Victoria Island training advice. Stable WZ/map facts deliberately stay separate from
 * the generated drop overlay so drop-table changes do not invalidate map-strategy tuning.
 */
public record AgentVictoriaTrainingCatalog(
        int schemaVersion,
        String catalogId,
        String gameDataVersion,
        DropOverlayContract dropOverlay,
        SelectionPolicy selectionPolicy,
        List<EvidenceSource> evidenceSources,
        List<TrainingMap> trainingMaps,
        List<LevelPlan> levelPlans) {

    public AgentVictoriaTrainingCatalog {
        if (schemaVersion <= 0 || blank(catalogId) || blank(gameDataVersion) || dropOverlay == null
                || selectionPolicy == null
                || evidenceSources == null || evidenceSources.isEmpty()
                || trainingMaps == null || trainingMaps.isEmpty()
                || levelPlans == null || levelPlans.isEmpty()) {
            throw new IllegalArgumentException("complete versioned Victoria training content is required");
        }
        evidenceSources = List.copyOf(evidenceSources);
        trainingMaps = List.copyOf(trainingMaps);
        levelPlans = List.copyOf(levelPlans);
    }

    public record SelectionPolicy(
            String rankingMode,
            boolean preserveCurrentMapWhenEligible,
            int currentMapMaximumRank,
            int fallbackLevelLookback,
            String softCapacityField,
            String hardCapacityField) {

        public SelectionPolicy {
            if (blank(rankingMode) || currentMapMaximumRank < 1 || fallbackLevelLookback < 0
                    || blank(softCapacityField) || blank(hardCapacityField)) {
                throw new IllegalArgumentException("a complete ranked training-selection policy is required");
            }
        }
    }

    public record DropOverlayContract(
            int schemaVersion,
            String generatorPath,
            String defaultDropCatalogPath) {

        public DropOverlayContract {
            if (schemaVersion <= 0 || blank(generatorPath) || blank(defaultDropCatalogPath)) {
                throw new IllegalArgumentException("a versioned drop-overlay contract is required");
            }
        }
    }

    public record EvidenceSource(
            String sourceId,
            String title,
            String url,
            String evidenceKind) {

        public EvidenceSource {
            if (blank(sourceId) || blank(title) || blank(url) || blank(evidenceKind)) {
                throw new IllegalArgumentException("complete training evidence provenance is required");
            }
        }
    }

    public record TrainingMap(
            int mapId,
            String mapName,
            String sourcePath,
            int recommendedMinLevel,
            int recommendedMaxLevel,
            int recommendedAgents,
            int maximumAgents,
            String terrain,
            List<String> tags,
            List<String> hazards,
            List<String> evidenceSourceIds,
            List<SpawnGroup> spawns) {

        public TrainingMap {
            if (mapId <= 0 || blank(mapName) || blank(sourcePath)
                    || recommendedMinLevel < 1 || recommendedMaxLevel < recommendedMinLevel
                    || recommendedAgents < 1 || maximumAgents < recommendedAgents || blank(terrain)
                    || tags == null || hazards == null || evidenceSourceIds == null
                    || spawns == null || spawns.isEmpty()) {
                throw new IllegalArgumentException("complete Victoria training-map facts are required");
            }
            tags = List.copyOf(tags);
            hazards = List.copyOf(hazards);
            evidenceSourceIds = List.copyOf(evidenceSourceIds);
            spawns = List.copyOf(spawns);
        }
    }

    public record SpawnGroup(
            int mobId,
            String mobName,
            int mobLevel,
            int expectedCount,
            String role) {

        public SpawnGroup {
            if (mobId <= 0 || blank(mobName) || mobLevel < 1 || expectedCount < 1 || blank(role)) {
                throw new IllegalArgumentException("valid WZ spawn facts are required");
            }
        }
    }

    public record LevelPlan(int level, List<TrainingChoice> choices) {
        public LevelPlan {
            if (level < 1 || choices == null || choices.isEmpty()) {
                throw new IllegalArgumentException("a level plan requires at least one training choice");
            }
            choices = List.copyOf(choices);
        }
    }

    public record TrainingChoice(
            int mapId,
            int rank,
            int weight,
            String rationale,
            List<String> conditions) {

        public TrainingChoice {
            if (mapId <= 0 || rank <= 0 || weight <= 0 || blank(rationale) || conditions == null) {
                throw new IllegalArgumentException("a weighted training choice is required");
            }
            conditions = List.copyOf(conditions);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
