package server.agents.catalog;

import java.util.List;
import java.util.Optional;

public final class QuestCatalogQuery {
    private final CatalogIndexes indexes;

    QuestCatalogQuery(CatalogBundle bundle) {
        this.indexes = bundle.indexes();
    }

    public Optional<CatalogRecord> findById(int questId) {
        return Optional.ofNullable(indexes.questsById.get(questId));
    }

    public Optional<CatalogRecord> objectivePlan(int questId) {
        return Optional.ofNullable(indexes.questObjectivesById.get(questId));
    }

    public List<CatalogRecord> actionsForQuest(int questId) {
        return indexes.npcActionsByQuestId.getOrDefault(questId, List.of());
    }

    public List<CatalogRecord> startActionsForQuest(int questId) {
        return actionsForQuest(questId).stream()
                .filter(action -> "quest-start".equalsIgnoreCase(action.stringValue("actionType").orElse("")))
                .toList();
    }

    public List<CatalogRecord> completeActionsForQuest(int questId) {
        return actionsForQuest(questId).stream()
                .filter(action -> "quest-complete".equalsIgnoreCase(action.stringValue("actionType").orElse("")))
                .toList();
    }

    public List<CatalogRecord> requiredItemObjectives(int questId) {
        return objectivePlan(questId)
                .map(plan -> plan.recordList("objectives").stream()
                        .filter(objective -> objective.stringValue("type").orElse("").contains("item")
                                || !objective.record("preconditions")
                                .map(preconditions -> preconditions.recordList("items").isEmpty())
                                .orElse(true))
                        .toList())
                .orElse(List.of());
    }

    public List<CatalogRecord> killObjectives(int questId) {
        return objectivePlan(questId)
                .map(plan -> plan.recordList("objectives").stream()
                        .filter(objective -> objective.stringValue("type").orElse("").contains("kill"))
                        .toList())
                .orElse(List.of());
    }

    public Optional<CatalogRecord> victoriaLt30Status(int questId) {
        return indexes.victoriaQuestStatuses.stream()
                .filter(status -> status.intValue("questId").orElse(-1) == questId)
                .findFirst();
    }

    public Optional<CatalogRecord> victoriaLt30HuntingPlan(int questId) {
        return Optional.ofNullable(indexes.victoriaQuestHuntingById.get(questId));
    }
}
