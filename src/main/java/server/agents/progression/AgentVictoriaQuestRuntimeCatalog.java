package server.agents.progression;

import java.util.List;

record AgentVictoriaQuestRuntimeCatalog(
        int schemaVersion,
        String catalogId,
        String sourceCatalogId,
        String sourceRevision,
        String sourceSha256,
        List<Entry> entries) {

    AgentVictoriaQuestRuntimeCatalog {
        if (schemaVersion <= 0 || blank(catalogId) || blank(sourceCatalogId)
                || blank(sourceRevision) || blank(sourceSha256) || entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("a complete Victoria quest runtime catalog is required");
        }
        entries = List.copyOf(entries);
    }

    record Entry(int questId, String questName, Integer minLevel, Integer maxLevel,
                 int startNpcId, List<Integer> startMapIds,
                 int completeNpcId, List<Integer> completeMapIds,
                 List<HuntingObjective> huntingObjectives) {
        Entry {
            if (questId <= 0 || startNpcId <= 0 || completeNpcId <= 0
                    || startMapIds == null || startMapIds.isEmpty()
                    || completeMapIds == null || completeMapIds.isEmpty()
                    || huntingObjectives == null || huntingObjectives.isEmpty()) {
                throw new IllegalArgumentException("a runnable hunting quest requires NPCs, maps, and objectives");
            }
            questName = questName == null ? "" : questName;
            startMapIds = List.copyOf(startMapIds);
            completeMapIds = List.copyOf(completeMapIds);
            huntingObjectives = List.copyOf(huntingObjectives);
        }

        boolean levelEligible(int level) {
            return (minLevel == null || level >= minLevel) && (maxLevel == null || level <= maxLevel);
        }
    }

    record HuntingObjective(String objectiveId, String type, int targetId, int requiredCount,
                            List<Integer> sourceMobIds, List<HuntMap> huntMaps) {
        HuntingObjective {
            if (blank(objectiveId) || blank(type) || targetId <= 0 || requiredCount <= 0
                    || sourceMobIds == null || sourceMobIds.isEmpty()
                    || huntMaps == null || huntMaps.isEmpty()) {
                throw new IllegalArgumentException("a hunting objective requires targets and maps");
            }
            sourceMobIds = List.copyOf(sourceMobIds);
            huntMaps = List.copyOf(huntMaps);
        }
    }

    record HuntMap(int rank, int mapId, int recommendedAgents, int maximumAgents,
                   List<Integer> targetMobIds) {
        HuntMap {
            if (rank <= 0 || mapId <= 0 || recommendedAgents <= 0
                    || maximumAgents < recommendedAgents || targetMobIds == null
                    || targetMobIds.isEmpty()) {
                throw new IllegalArgumentException("a ranked hunt map requires capacity and target mobs");
            }
            targetMobIds = List.copyOf(targetMobIds);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
