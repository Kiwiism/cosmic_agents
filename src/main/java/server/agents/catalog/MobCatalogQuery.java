package server.agents.catalog;

import java.util.List;
import java.util.Optional;

public final class MobCatalogQuery {
    private final CatalogIndexes indexes;

    MobCatalogQuery(CatalogBundle bundle) {
        this.indexes = bundle.indexes();
    }

    public Optional<CatalogRecord> findById(int mobId) {
        return Optional.ofNullable(indexes.mobsById.get(mobId));
    }

    public List<CatalogRecord> findByName(String name) {
        return indexes.mobsByName.getOrDefault(normalize(name), List.of());
    }

    public List<CatalogRecord> spawnMaps(int mobId) {
        return indexes.mobSpawnMapsByMobId.getOrDefault(mobId, List.of());
    }

    public Optional<CatalogRecord> spawnSummaryForMap(int mapId) {
        return Optional.ofNullable(indexes.mobSpawnByMapId.get(mapId));
    }

    public List<CatalogRecord> dropsForMob(int mobId) {
        return indexes.dropEntriesByMobId.getOrDefault(mobId, List.of());
    }

    public List<CatalogRecord> mobsDroppingItem(int itemId) {
        return Optional.ofNullable(indexes.itemSourcesByItemId.get(itemId))
                .map(source -> source.recordList("dropSources").stream()
                        .filter(drop -> "mob".equalsIgnoreCase(drop.stringValue("sourceType").orElse("")))
                        .toList())
                .orElse(List.of());
    }

    private static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
    }
}
