package server.agents.progression;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Versioned, source-controlled content needed by the Victoria level-15 vertical slice. */
public record AgentVictoriaLevel15Catalog(
        int schemaVersion,
        String catalogId,
        IslandHandoff islandHandoff,
        List<StartVariant> startVariants,
        List<RouteCorridor> routeCorridors,
        List<ScriptedPortal> scriptedPortals,
        List<QuestPack> questPacks,
        List<InteractionQuest> interactionQuests,
        List<Career> careers) {

    public AgentVictoriaLevel15Catalog {
        if (schemaVersion <= 0 || blank(catalogId) || islandHandoff == null
                || startVariants == null || startVariants.isEmpty()
                || routeCorridors == null || routeCorridors.isEmpty()
                || scriptedPortals == null || questPacks == null || questPacks.isEmpty()
                || interactionQuests == null || careers == null || careers.isEmpty()) {
            throw new IllegalArgumentException("a versioned Victoria level-15 catalog is required");
        }
        startVariants = List.copyOf(startVariants);
        routeCorridors = List.copyOf(routeCorridors);
        scriptedPortals = List.copyOf(scriptedPortals);
        questPacks = List.copyOf(questPacks);
        interactionQuests = List.copyOf(interactionQuests);
        careers = List.copyOf(careers);
    }

    public record IslandHandoff(
            int lithHarborMapId,
            int olafNpcId,
            int biggsNpcId,
            int biggsQuestId,
            int biggsRewardExp,
            int olafLessonQuestId,
            int olafLessonRewardExp,
            int targetLevel,
            int grindMapId,
            List<Integer> grindMobIds) {

        public IslandHandoff {
            if (lithHarborMapId <= 0 || olafNpcId <= 0 || biggsNpcId <= 0 || biggsQuestId <= 0
                    || biggsRewardExp <= 0 || olafLessonQuestId <= 0 || olafLessonRewardExp <= 0
                    || targetLevel <= 0 || grindMapId <= 0 || grindMobIds == null || grindMobIds.isEmpty()
                    || grindMobIds.stream().anyMatch(mobId -> mobId == null || mobId <= 0)) {
                throw new IllegalArgumentException("complete Maple Island handoff content is required");
            }
            grindMobIds = List.copyOf(grindMobIds);
        }
    }

    public record StartVariant(String variantId, int level, int exp, boolean expectsPreJobGrind) {
        public StartVariant {
            if (blank(variantId) || level <= 0 || exp < 0) {
                throw new IllegalArgumentException("a valid Victoria start variant is required");
            }
        }
    }

    public record RouteCorridor(String corridorId, List<Integer> mapIds) {
        public RouteCorridor {
            if (blank(corridorId) || mapIds == null || mapIds.size() < 2
                    || mapIds.stream().anyMatch(mapId -> mapId == null || mapId <= 0)) {
                throw new IllegalArgumentException("a route corridor requires an id and at least two maps");
            }
            mapIds = List.copyOf(mapIds);
        }
    }

    public record ScriptedPortal(int sourceMapId, int destinationMapId, int portalId) {
        public ScriptedPortal {
            if (sourceMapId <= 0 || destinationMapId <= 0 || portalId < 0) {
                throw new IllegalArgumentException("valid scripted portal fields are required");
            }
        }
    }

    public record QuestPack(String packId, List<Integer> questIds) {
        public QuestPack {
            if (blank(packId) || questIds == null || questIds.isEmpty()
                    || questIds.stream().anyMatch(questId -> questId == null || questId <= 0)
                    || Set.copyOf(questIds).size() != questIds.size()) {
                throw new IllegalArgumentException("a quest pack requires an id and unique quest ids");
            }
            questIds = List.copyOf(questIds);
        }
    }

    /** Quest whose start/finish interaction is valid but has no hunting objective catalog entry. */
    public record InteractionQuest(
            int questId,
            int startNpcId,
            int startMapId,
            int completeNpcId,
            int completeMapId) {
        public InteractionQuest {
            if (questId <= 0 || startNpcId <= 0 || startMapId <= 0
                    || completeNpcId <= 0 || completeMapId <= 0) {
                throw new IllegalArgumentException("complete interaction-only quest content is required");
            }
        }
    }

    public enum AfterHomeStrategy {
        LOCAL_GRIND,
        ROTATION_PACK
    }

    public record CatchUpPlan(
            String homePackId,
            AfterHomeStrategy afterHomeStrategy,
            String rotationPackId,
            MilestoneGrind fallbackGrind) {
        public CatchUpPlan {
            rotationPackId = rotationPackId == null ? "" : rotationPackId.trim();
            if (blank(homePackId) || afterHomeStrategy == null || fallbackGrind == null
                    || (afterHomeStrategy == AfterHomeStrategy.ROTATION_PACK
                    && blank(rotationPackId))
                    || (afterHomeStrategy == AfterHomeStrategy.LOCAL_GRIND
                    && !rotationPackId.isEmpty())) {
                throw new IllegalArgumentException("a valid level-15 catch-up plan is required");
            }
        }
    }

    public record Career(
            int firstJobId,
            List<String> supportedBundleIds,
            int olafPathQuestId,
            int taxiSelection,
            int townMapId,
            int instructorNpcId,
            int instructorMapId,
            int shopNpcId,
            int shopMapId,
            List<Integer> starterKitItemIds,
            Map<String, Integer> preferredStarterWeaponByBundleId,
            List<Integer> verifiedShopItemIds,
            List<TrainingStep> trainingSteps,
            MilestoneGrind milestoneGrind,
            CatchUpPlan catchUpPlan) {

        public Career {
            Set<String> supportedBundles = supportedBundleIds == null
                    ? Set.of() : Set.copyOf(supportedBundleIds);
            Set<Integer> starterKitItems = starterKitItemIds == null
                    ? Set.of() : Set.copyOf(starterKitItemIds);
            if (firstJobId <= 0 || supportedBundleIds == null || supportedBundleIds.isEmpty()
                    || supportedBundleIds.stream().anyMatch(AgentVictoriaLevel15Catalog::blank)
                    || olafPathQuestId <= 0 || taxiSelection < 0 || townMapId <= 0 || instructorNpcId <= 0
                    || instructorMapId <= 0 || shopNpcId <= 0 || shopMapId <= 0
                    || starterKitItemIds == null || starterKitItemIds.isEmpty()
                    || starterKitItemIds.stream().anyMatch(itemId -> itemId == null || itemId <= 0)
                    || preferredStarterWeaponByBundleId == null
                    || !preferredStarterWeaponByBundleId.keySet().equals(supportedBundles)
                    || preferredStarterWeaponByBundleId.values().stream()
                    .anyMatch(itemId -> itemId == null || !starterKitItems.contains(itemId))
                    || verifiedShopItemIds == null || verifiedShopItemIds.isEmpty()
                    || verifiedShopItemIds.stream().anyMatch(itemId -> itemId == null || itemId <= 0)
                    || trainingSteps == null || trainingSteps.isEmpty() || milestoneGrind == null
                    || catchUpPlan == null) {
                throw new IllegalArgumentException("complete Victoria career content is required");
            }
            supportedBundleIds = List.copyOf(supportedBundleIds);
            starterKitItemIds = List.copyOf(starterKitItemIds);
            preferredStarterWeaponByBundleId = Map.copyOf(preferredStarterWeaponByBundleId);
            verifiedShopItemIds = List.copyOf(verifiedShopItemIds);
            trainingSteps = List.copyOf(trainingSteps);
        }
    }

    public record TrainingStep(
            int questId,
            int huntingMapId,
            List<Integer> mobIds,
            List<Integer> requiredCounts,
            int rewardExp) {

        public TrainingStep {
            if (questId <= 0 || huntingMapId <= 0 || mobIds == null || mobIds.isEmpty()
                    || requiredCounts == null || requiredCounts.size() != mobIds.size()
                    || mobIds.stream().anyMatch(mobId -> mobId == null || mobId <= 0)
                    || requiredCounts.stream().anyMatch(count -> count == null || count <= 0)
                    || rewardExp < 0) {
                throw new IllegalArgumentException("valid quest, map, mob, count, and reward data are required");
            }
            mobIds = List.copyOf(mobIds);
            requiredCounts = List.copyOf(requiredCounts);
        }
    }

    public record MilestoneGrind(int huntingMapId, List<Integer> mobIds) {
        public MilestoneGrind {
            if (huntingMapId <= 0 || mobIds == null || mobIds.isEmpty()
                    || mobIds.stream().anyMatch(mobId -> mobId == null || mobId <= 0)) {
                throw new IllegalArgumentException("a valid milestone grind map and mobs are required");
            }
            mobIds = List.copyOf(mobIds);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
