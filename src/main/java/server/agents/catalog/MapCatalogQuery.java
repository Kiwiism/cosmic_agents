package server.agents.catalog;

import java.util.List;
import java.util.Optional;

public final class MapCatalogQuery {
    private final CatalogIndexes indexes;
    private final NpcCatalogQuery npcQuery;

    MapCatalogQuery(CatalogBundle bundle, NpcCatalogQuery npcQuery) {
        this.indexes = bundle.indexes();
        this.npcQuery = npcQuery;
    }

    public Optional<CatalogRecord> findById(int mapId) {
        return Optional.ofNullable(indexes.mapsById.get(mapId));
    }

    public List<CatalogRecord> findByName(String name) {
        return indexes.mapsByName.getOrDefault(normalize(name), List.of());
    }

    public Optional<CatalogRecord> summary(int mapId) {
        return Optional.ofNullable(indexes.mapSummaryById.get(mapId));
    }

    public List<CatalogRecord> npcsInMap(int mapId) {
        return npcQuery.npcsInMap(mapId);
    }

    public Optional<CatalogRecord> mobSpawnSummary(int mapId) {
        return Optional.ofNullable(indexes.mobSpawnByMapId.get(mapId));
    }

    public List<CatalogRecord> mobsInMap(int mapId) {
        return mobSpawnSummary(mapId).map(record -> record.recordList("mobs")).orElse(List.of());
    }

    public List<CatalogRecord> portalEdgesFrom(int mapId) {
        return indexes.portalEdgesByMapId.getOrDefault(mapId, List.of());
    }

    public List<Integer> connectedMapIds(int mapId) {
        return portalEdgesFrom(mapId).stream()
                .flatMap(edge -> edge.intValue("toMapId").stream())
                .distinct()
                .toList();
    }

    public boolean isMapleIslandRelevantMap(int mapId) {
        return indexes.mapleIslandRouteFactsByMapId.containsKey(mapId);
    }

    private static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
    }
}
