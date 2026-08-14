package server.agents.catalog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable, precomputed joins over the generated catalogs for server-side agent planning.
 * One instance is shared by every consumer of a {@link CatalogQueryService}.
 */
public final class ServerKnowledgeCatalogQuery {
    private final Map<Integer, MapKnowledge> maps;
    private final Map<Integer, MobKnowledge> mobs;
    private final Map<Integer, ItemKnowledge> items;
    private final Map<Integer, QuestKnowledge> quests;

    ServerKnowledgeCatalogQuery(CatalogBundle bundle) {
        CatalogIndexes indexes = bundle.indexes();
        this.maps = buildMaps(indexes);
        this.mobs = buildMobs(indexes);
        this.items = buildItems(indexes, mobs);
        this.quests = buildQuests(indexes, items, mobs);
    }

    public Optional<MapKnowledge> map(int mapId) {
        return Optional.ofNullable(maps.get(mapId));
    }

    public Optional<MobKnowledge> mob(int mobId) {
        return Optional.ofNullable(mobs.get(mobId));
    }

    public Optional<ItemKnowledge> item(int itemId) {
        return Optional.ofNullable(items.get(itemId));
    }

    public Optional<QuestKnowledge> quest(int questId) {
        return Optional.ofNullable(quests.get(questId));
    }

    public int mapCount() {
        return maps.size();
    }

    public int mobCount() {
        return mobs.size();
    }

    public int itemCount() {
        return items.size();
    }

    public int questCount() {
        return quests.size();
    }

    private static Map<Integer, MapKnowledge> buildMaps(CatalogIndexes indexes) {
        Set<Integer> mapIds = new HashSet<>(indexes.mapsById.keySet());
        mapIds.addAll(indexes.mobSpawnByMapId.keySet());
        mapIds.addAll(indexes.npcPlacementsByMapId.keySet());
        mapIds.addAll(indexes.portalEdgesByMapId.keySet());
        mapIds.addAll(indexes.reactorsByMapId.keySet());

        Map<Integer, MapKnowledge> result = new HashMap<>();
        for (int mapId : mapIds) {
            CatalogRecord spawn = indexes.mobSpawnByMapId.get(mapId);
            List<Integer> mobIds = (spawn == null ? List.<CatalogRecord>of() : spawn.recordList("mobs")).stream()
                    .flatMap(record -> record.intValue("mobId").stream())
                    .distinct()
                    .sorted()
                    .toList();
            List<Integer> npcIds = indexes.npcPlacementsByMapId.getOrDefault(mapId, List.of()).stream()
                    .flatMap(record -> record.intValue("npcId").stream())
                    .distinct()
                    .sorted()
                    .toList();
            List<Integer> reactorIds = indexes.reactorsByMapId.getOrDefault(mapId, List.of()).stream()
                    .flatMap(record -> record.intValue("reactorId").stream())
                    .distinct()
                    .sorted()
                    .toList();
            List<Integer> connectedMapIds = indexes.portalEdgesByMapId.getOrDefault(mapId, List.of()).stream()
                    .flatMap(record -> record.intValue("toMapId").stream())
                    .distinct()
                    .sorted()
                    .toList();
            result.put(mapId, new MapKnowledge(mapId, mobIds, npcIds, reactorIds, connectedMapIds));
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, MobKnowledge> buildMobs(CatalogIndexes indexes) {
        Set<Integer> mobIds = new HashSet<>(indexes.mobsById.keySet());
        mobIds.addAll(indexes.mobSpawnMapsByMobId.keySet());
        mobIds.addAll(indexes.dropEntriesByMobId.keySet());

        Map<Integer, MobKnowledge> result = new HashMap<>();
        for (int mobId : mobIds) {
            List<Integer> spawnMapIds = indexes.mobSpawnMapsByMobId.getOrDefault(mobId, List.of()).stream()
                    .flatMap(record -> record.intValue("mapId").stream())
                    .distinct()
                    .sorted()
                    .toList();
            List<Integer> dropItemIds = indexes.dropEntriesByMobId.getOrDefault(mobId, List.of()).stream()
                    .flatMap(record -> record.intValue("itemId").stream())
                    .distinct()
                    .sorted()
                    .toList();
            result.put(mobId, new MobKnowledge(mobId, spawnMapIds, dropItemIds));
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, ItemKnowledge> buildItems(CatalogIndexes indexes,
                                                           Map<Integer, MobKnowledge> mobs) {
        Set<Integer> itemIds = new HashSet<>(indexes.itemsById.keySet());
        itemIds.addAll(indexes.itemSourcesByItemId.keySet());
        itemIds.addAll(indexes.dropEntriesByItemId.keySet());

        Map<Integer, ItemKnowledge> result = new HashMap<>();
        for (int itemId : itemIds) {
            List<MobDropSource> mobSources = new ArrayList<>();
            Set<Integer> sourceMapIds = new TreeSet<>();
            CatalogRecord source = indexes.itemSourcesByItemId.get(itemId);
            if (source != null) {
                for (CatalogRecord dropSource : source.recordList("dropSources")) {
                    if (!"mob".equalsIgnoreCase(dropSource.stringValue("sourceType").orElse(""))) {
                        continue;
                    }
                    dropSource.intValue("sourceId").ifPresent(mobId -> {
                        Set<Integer> mapsForMob = new TreeSet<>(ids(dropSource, "mapIds"));
                        if (mapsForMob.isEmpty()) {
                            Optional.ofNullable(mobs.get(mobId)).ifPresent(mob -> mapsForMob.addAll(mob.spawnMapIds()));
                        }
                        sourceMapIds.addAll(mapsForMob);
                        mobSources.add(new MobDropSource(
                                mobId,
                                dropSource.longValue("chance").orElse(0L),
                                dropSource.intValue("questId").orElse(0),
                                List.copyOf(mapsForMob)));
                    });
                }
            }
            mobSources.sort((left, right) -> Integer.compare(left.mobId(), right.mobId()));
            result.put(itemId, new ItemKnowledge(itemId, mobSources, List.copyOf(sourceMapIds)));
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, QuestKnowledge> buildQuests(CatalogIndexes indexes,
                                                             Map<Integer, ItemKnowledge> items,
                                                             Map<Integer, MobKnowledge> mobs) {
        Set<Integer> questIds = new HashSet<>(indexes.questsById.keySet());
        questIds.addAll(indexes.questObjectivesById.keySet());

        Map<Integer, QuestKnowledge> result = new HashMap<>();
        for (int questId : questIds) {
            CatalogRecord plan = indexes.questObjectivesById.get(questId);
            CatalogRecord quest = indexes.questsById.get(questId);
            int startNpcId = value(plan, quest, "startNpcId");
            int completeNpcId = value(plan, quest, "completeNpcId");
            Set<Integer> itemIds = new TreeSet<>();
            Set<Integer> mobIds = new TreeSet<>();
            Set<Integer> relevantMapIds = new TreeSet<>();

            if (plan != null) {
                for (CatalogRecord objective : plan.recordList("objectives")) {
                    objective.intValue("itemId").ifPresent(itemIds::add);
                    objective.intValue("mobId").ifPresent(mobIds::add);
                    relevantMapIds.addAll(ids(objective, "candidateMapIds"));
                    objective.record("preconditions").ifPresent(preconditions -> {
                        preconditions.recordList("items").stream()
                                .flatMap(record -> record.intValue("itemId").stream())
                                .forEach(itemIds::add);
                        preconditions.recordList("mobs").stream()
                                .flatMap(record -> record.intValue("mobId").stream())
                                .forEach(mobIds::add);
                    });
                }
            }
            addNpcMaps(indexes, startNpcId, relevantMapIds);
            addNpcMaps(indexes, completeNpcId, relevantMapIds);
            itemIds.stream().map(items::get).filter(Objects::nonNull)
                    .forEach(item -> relevantMapIds.addAll(item.sourceMapIds()));
            mobIds.stream().map(mobs::get).filter(Objects::nonNull)
                    .forEach(mob -> relevantMapIds.addAll(mob.spawnMapIds()));

            result.put(questId, new QuestKnowledge(
                    questId,
                    startNpcId,
                    completeNpcId,
                    List.copyOf(itemIds),
                    List.copyOf(mobIds),
                    List.copyOf(relevantMapIds)));
        }
        return Map.copyOf(result);
    }

    private static void addNpcMaps(CatalogIndexes indexes, int npcId, Set<Integer> target) {
        if (npcId <= 0) {
            return;
        }
        indexes.npcPlacementsByNpcId.getOrDefault(npcId, List.of()).stream()
                .flatMap(record -> record.intValue("mapId").stream())
                .forEach(target::add);
    }

    private static int value(CatalogRecord preferred, CatalogRecord fallback, String field) {
        if (preferred != null) {
            Optional<Integer> value = preferred.intValue(field);
            if (value.isPresent()) {
                return value.get();
            }
        }
        return fallback == null ? 0 : fallback.intValue(field).orElse(0);
    }

    private static List<Integer> ids(CatalogRecord record, String field) {
        Set<Integer> ids = new TreeSet<>(record.intList(field));
        record.intValue(field).ifPresent(ids::add);
        return List.copyOf(ids);
    }

    public record MapKnowledge(int mapId,
                               List<Integer> mobIds,
                               List<Integer> npcIds,
                               List<Integer> reactorIds,
                               List<Integer> connectedMapIds) {
        public MapKnowledge {
            mobIds = List.copyOf(mobIds);
            npcIds = List.copyOf(npcIds);
            reactorIds = List.copyOf(reactorIds);
            connectedMapIds = List.copyOf(connectedMapIds);
        }
    }

    public record MobKnowledge(int mobId, List<Integer> spawnMapIds, List<Integer> dropItemIds) {
        public MobKnowledge {
            spawnMapIds = List.copyOf(spawnMapIds);
            dropItemIds = List.copyOf(dropItemIds);
        }
    }

    public record MobDropSource(int mobId, long chance, int questId, List<Integer> mapIds) {
        public MobDropSource {
            mapIds = List.copyOf(mapIds);
        }
    }

    public record ItemKnowledge(int itemId, List<MobDropSource> mobSources, List<Integer> sourceMapIds) {
        public ItemKnowledge {
            mobSources = List.copyOf(mobSources);
            sourceMapIds = List.copyOf(sourceMapIds);
        }
    }

    public record QuestKnowledge(int questId,
                                 int startNpcId,
                                 int completeNpcId,
                                 List<Integer> requiredItemIds,
                                 List<Integer> requiredMobIds,
                                 List<Integer> relevantMapIds) {
        public QuestKnowledge {
            requiredItemIds = List.copyOf(requiredItemIds);
            requiredMobIds = List.copyOf(requiredMobIds);
            relevantMapIds = List.copyOf(relevantMapIds);
        }
    }
}
