package server.agents.catalog;

import java.util.List;
import java.util.Optional;

public final class MapleIslandMvpCatalogQuery {
    private final CatalogBundle bundle;
    private final CatalogIndexes indexes;

    MapleIslandMvpCatalogQuery(CatalogBundle bundle) {
        this.bundle = bundle;
        this.indexes = bundle.indexes();
    }

    public CatalogRecord plan() {
        return CatalogRecord.from(bundle.node(CatalogFile.MAPLE_ISLAND_MVP), bundle.mapper());
    }

    public Optional<CatalogRecord> questRule(int questId) {
        return Optional.ofNullable(indexes.mapleIslandQuestRuleByQuestId.get(questId));
    }

    public List<Integer> questIdsInMvpSequence() {
        return indexes.mapleIslandQuestRuleByQuestId.keySet().stream()
                .sorted()
                .toList();
    }

    public Optional<CatalogRecord> objective(String objectiveId) {
        return Optional.ofNullable(indexes.mapleIslandObjectiveById.get(objectiveId));
    }

    public List<CatalogRecord> objectivesForQuest(int questId) {
        return questRule(questId).map(rule -> rule.recordList("objectives")).orElse(List.of());
    }

    public Optional<CatalogRecord> specialRule(String ruleId) {
        return indexes.mapleIslandSpecialRules.stream()
                .filter(rule -> ruleId.equals(rule.stringValue("ruleId").orElse("")))
                .findFirst();
    }

    public List<CatalogRecord> forbiddenActions() {
        return List.copyOf(indexes.mapleIslandForbiddenActions);
    }

    public boolean isForbiddenNpcTravel(int npcId) {
        return indexes.mapleIslandForbiddenActions.stream()
                .anyMatch(action -> "npc-travel".equals(action.stringValue("type").orElse(""))
                        && action.intValue("npcId").orElse(-1) == npcId);
    }

    public boolean isForbiddenQuestComplete(int questId) {
        return indexes.mapleIslandForbiddenActions.stream()
                .filter(action -> "quest-complete".equals(action.stringValue("type").orElse("")))
                .flatMap(action -> action.intList("questIds").stream())
                .anyMatch(id -> id == questId);
    }

    public Optional<CatalogRecord> routeFactsForMap(int mapId) {
        return Optional.ofNullable(indexes.mapleIslandRouteFactsByMapId.get(mapId));
    }
}
