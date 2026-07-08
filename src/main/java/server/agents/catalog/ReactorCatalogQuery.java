package server.agents.catalog;

import java.util.List;

public final class ReactorCatalogQuery {
    private static final int PIO_QUEST_ID = 1008;
    private static final int PIO_USED_BOX_ITEM_ID = 4031161;
    private static final int PIO_RECYCLABLE_BOX_ITEM_ID = 4031162;

    private final CatalogIndexes indexes;

    ReactorCatalogQuery(CatalogBundle bundle) {
        this.indexes = bundle.indexes();
    }

    public List<CatalogRecord> allReactors() {
        return indexes.reactorsByMapId.values().stream()
                .flatMap(List::stream)
                .toList();
    }

    public List<CatalogRecord> reactorsInMap(int mapId) {
        return indexes.reactorsByMapId.getOrDefault(mapId, List.of());
    }

    public List<CatalogRecord> findReactorById(int reactorId) {
        return indexes.reactorsById.getOrDefault(reactorId, List.of());
    }

    public List<CatalogRecord> findReactorsForQuest(int questId) {
        return indexes.reactorsByQuestId.getOrDefault(questId, List.of());
    }

    public List<CatalogRecord> findReactorsDroppingItem(int itemId) {
        return indexes.reactorsByDroppedItemId.getOrDefault(itemId, List.of());
    }

    public List<CatalogRecord> mapleIslandPioReactors() {
        List<CatalogRecord> byQuest = findReactorsForQuest(PIO_QUEST_ID);
        if (!byQuest.isEmpty()) {
            return byQuest;
        }
        List<CatalogRecord> usedBoxes = findReactorsDroppingItem(PIO_USED_BOX_ITEM_ID);
        if (!usedBoxes.isEmpty()) {
            return usedBoxes;
        }
        return findReactorsDroppingItem(PIO_RECYCLABLE_BOX_ITEM_ID);
    }
}
