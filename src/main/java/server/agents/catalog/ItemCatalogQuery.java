package server.agents.catalog;

import java.util.List;
import java.util.Optional;

public final class ItemCatalogQuery {
    private final CatalogIndexes indexes;

    ItemCatalogQuery(CatalogBundle bundle) {
        this.indexes = bundle.indexes();
    }

    public Optional<CatalogRecord> findById(int itemId) {
        return Optional.ofNullable(indexes.itemsById.get(itemId));
    }

    public List<CatalogRecord> findByName(String name) {
        return indexes.itemsByName.getOrDefault(normalize(name), List.of());
    }

    public Optional<CatalogRecord> sourcesForItem(int itemId) {
        return Optional.ofNullable(indexes.itemSourcesByItemId.get(itemId));
    }

    public List<CatalogRecord> mobsDroppingItem(int itemId) {
        return sourcesForItem(itemId)
                .map(source -> source.recordList("dropSources").stream()
                        .filter(drop -> "mob".equalsIgnoreCase(drop.stringValue("sourceType").orElse("")))
                        .toList())
                .orElse(List.of());
    }

    public List<CatalogRecord> dropEntriesForItem(int itemId) {
        return indexes.dropEntriesByItemId.getOrDefault(itemId, List.of());
    }

    public List<CatalogRecord> dropSourceClassifications() {
        return List.copyOf(indexes.dropSourceClassifications);
    }

    private static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
    }
}
