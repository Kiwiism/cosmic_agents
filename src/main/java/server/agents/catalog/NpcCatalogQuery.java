package server.agents.catalog;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class NpcCatalogQuery {
    private final CatalogIndexes indexes;

    NpcCatalogQuery(CatalogBundle bundle) {
        this.indexes = bundle.indexes();
    }

    public Optional<CatalogRecord> findById(int npcId) {
        return Optional.ofNullable(indexes.npcsById.get(npcId));
    }

    public List<CatalogRecord> findByName(String name) {
        return indexes.npcsByName.getOrDefault(normalize(name), List.of());
    }

    public List<CatalogRecord> placementsForNpc(int npcId) {
        return indexes.npcPlacementsByNpcId.getOrDefault(npcId, List.of());
    }

    public List<CatalogRecord> npcsInMap(int mapId) {
        return indexes.npcPlacementsByMapId.getOrDefault(mapId, List.of());
    }

    public List<CatalogRecord> actionsForNpc(int npcId) {
        return indexes.npcActionsByNpcId.getOrDefault(npcId, List.of());
    }

    public List<CatalogRecord> actionsForQuest(int questId) {
        return indexes.npcActionsByQuestId.getOrDefault(questId, List.of());
    }

    public List<CatalogRecord> questStartActionsForNpc(int npcId) {
        return actionsForNpc(npcId).stream()
                .filter(action -> "quest-start".equalsIgnoreCase(action.stringValue("actionType").orElse("")))
                .toList();
    }

    public List<CatalogRecord> questCompleteActionsForNpc(int npcId) {
        return actionsForNpc(npcId).stream()
                .filter(action -> "quest-complete".equalsIgnoreCase(action.stringValue("actionType").orElse("")))
                .toList();
    }

    public List<CatalogRecord> shopOrServiceActionsForNpc(int npcId) {
        return actionsForNpc(npcId).stream()
                .filter(action -> {
                    String type = action.stringValue("actionType").orElse("");
                    return type.contains("shop") || type.contains("service");
                })
                .toList();
    }

    public Optional<CatalogRecord> shopOrServiceDetailsForNpc(int npcId) {
        return findById(npcId).flatMap(npc -> npc.record("interactions"));
    }

    public Optional<CatalogRecord> approachPointSet(int npcId, int mapId) {
        return Optional.ofNullable(indexes.npcApproachByNpcMap.get(CatalogIndexes.npcMapKey(npcId, mapId)));
    }

    public List<CatalogRecord> approachCandidates(int npcId, int mapId) {
        return approachPointSet(npcId, mapId)
                .map(record -> record.recordList("candidates"))
                .orElse(List.of());
    }

    public Optional<CatalogRecord> seededApproachCandidate(int npcId, int mapId, long seed) {
        List<CatalogRecord> candidates = approachCandidates(npcId, mapId).stream()
                .sorted(Comparator.comparingInt(candidate -> candidate.intValue("score").orElse(0)))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        int index = new Random(seed).nextInt(candidates.size());
        return Optional.of(candidates.get(index));
    }

    public Optional<CatalogRecord> dialogueTiming(int questId, String phase) {
        return Optional.ofNullable(indexes.dialogueByQuestPhase.get(CatalogIndexes.questPhaseKey(questId, phase)));
    }

    private static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
    }
}
