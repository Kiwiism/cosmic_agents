package server.agents.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class CatalogIndexes {
    final Map<Integer, CatalogRecord> mapsById = new HashMap<>();
    final Map<String, List<CatalogRecord>> mapsByName = new HashMap<>();
    final Map<Integer, CatalogRecord> mapSummaryById = new HashMap<>();
    final Map<Integer, CatalogRecord> mobsById = new HashMap<>();
    final Map<String, List<CatalogRecord>> mobsByName = new HashMap<>();
    final Map<Integer, CatalogRecord> itemsById = new HashMap<>();
    final Map<String, List<CatalogRecord>> itemsByName = new HashMap<>();
    final Map<Integer, CatalogRecord> questsById = new HashMap<>();
    final Map<Integer, CatalogRecord> questObjectivesById = new HashMap<>();
    final Map<Integer, CatalogRecord> npcsById = new HashMap<>();
    final Map<String, List<CatalogRecord>> npcsByName = new HashMap<>();
    final Map<Integer, List<CatalogRecord>> npcPlacementsByNpcId = new HashMap<>();
    final Map<Integer, List<CatalogRecord>> npcPlacementsByMapId = new HashMap<>();
    final Map<Integer, List<CatalogRecord>> npcActionsByNpcId = new HashMap<>();
    final Map<Integer, List<CatalogRecord>> npcActionsByQuestId = new HashMap<>();
    final Map<String, CatalogRecord> npcApproachByNpcMap = new HashMap<>();
    final Map<String, CatalogRecord> dialogueByQuestPhase = new HashMap<>();
    final Map<Integer, List<CatalogRecord>> portalEdgesByMapId = new HashMap<>();
    final Map<Integer, CatalogRecord> mobSpawnByMapId = new HashMap<>();
    final Map<Integer, List<CatalogRecord>> mobSpawnMapsByMobId = new HashMap<>();
    final Map<Integer, CatalogRecord> itemSourcesByItemId = new HashMap<>();
    final Map<Integer, List<CatalogRecord>> dropEntriesByItemId = new HashMap<>();
    final Map<Integer, List<CatalogRecord>> dropEntriesByMobId = new HashMap<>();
    final Map<Integer, List<CatalogRecord>> reactorsByMapId = new HashMap<>();
    final Map<Integer, List<CatalogRecord>> reactorsById = new HashMap<>();
    final Map<Integer, List<CatalogRecord>> reactorsByQuestId = new HashMap<>();
    final Map<Integer, List<CatalogRecord>> reactorsByDroppedItemId = new HashMap<>();
    final Map<Integer, CatalogRecord> mapleIslandQuestRuleByQuestId = new HashMap<>();
    final Map<String, CatalogRecord> mapleIslandObjectiveById = new HashMap<>();
    final Map<Integer, CatalogRecord> mapleIslandRouteFactsByMapId = new HashMap<>();
    final List<CatalogRecord> mapleIslandSpecialRules = new ArrayList<>();
    final List<CatalogRecord> mapleIslandForbiddenActions = new ArrayList<>();
    final List<CatalogRecord> dropSourceClassifications = new ArrayList<>();
    final List<CatalogRecord> victoriaQuestStatuses = new ArrayList<>();

    static CatalogIndexes build(CatalogBundle bundle, ObjectMapper mapper) {
        CatalogIndexes indexes = new CatalogIndexes();
        indexes.indexSimple(bundle.node(CatalogFile.MAPS), mapper, "mapId", indexes.mapsById, "mapName", indexes.mapsByName);
        indexes.indexSimple(bundle.node(CatalogFile.MAP_SUMMARY), mapper, "mapId", indexes.mapSummaryById, "label", indexes.mapsByName);
        indexes.indexSimple(bundle.node(CatalogFile.MOBS), mapper, "mobId", indexes.mobsById, "name", indexes.mobsByName);
        indexes.indexSimple(bundle.node(CatalogFile.ITEMS), mapper, "itemId", indexes.itemsById, "name", indexes.itemsByName);
        indexes.indexSimple(bundle.node(CatalogFile.QUESTS), mapper, "questId", indexes.questsById, null, null);
        indexes.indexSimple(bundle.node(CatalogFile.QUEST_OBJECTIVES), mapper, "questId", indexes.questObjectivesById, null, null);
        indexes.indexSimple(bundle.node(CatalogFile.NPCS), mapper, "npcId", indexes.npcsById, "name", indexes.npcsByName);

        indexes.indexList(bundle.node(CatalogFile.NPC_PLACEMENTS), mapper, "npcId", indexes.npcPlacementsByNpcId);
        indexes.indexList(bundle.node(CatalogFile.NPC_PLACEMENTS), mapper, "mapId", indexes.npcPlacementsByMapId);
        indexes.indexList(bundle.node(CatalogFile.NPC_ACTIONS), mapper, "npcId", indexes.npcActionsByNpcId);
        indexes.indexList(bundle.node(CatalogFile.NPC_ACTIONS), mapper, "questId", indexes.npcActionsByQuestId);
        indexes.indexList(bundle.node(CatalogFile.PORTAL_GRAPH), mapper, "fromMapId", indexes.portalEdgesByMapId);
        indexes.indexList(bundle.node(CatalogFile.DROPS), mapper, "itemId", indexes.dropEntriesByItemId);

        for (CatalogRecord drop : records(bundle.node(CatalogFile.DROPS), mapper)) {
            if ("mob".equalsIgnoreCase(drop.stringValue("sourceType").orElse(""))) {
                drop.intValue("sourceId").ifPresent(mobId -> add(indexes.dropEntriesByMobId, mobId, drop));
            }
        }

        for (CatalogRecord approach : records(bundle.node(CatalogFile.NPC_APPROACH_POINTS), mapper)) {
            Optional<Integer> npcId = approach.intValue("npcId");
            Optional<Integer> mapId = approach.intValue("mapId");
            if (npcId.isPresent() && mapId.isPresent()) {
                indexes.npcApproachByNpcMap.put(npcMapKey(npcId.get(), mapId.get()), approach);
            }
        }

        for (CatalogRecord timing : records(bundle.node(CatalogFile.QUEST_DIALOGUE_TIMING), mapper)) {
            Optional<Integer> questId = timing.intValue("questId");
            String phase = timing.stringValue("phase").orElse("");
            if (questId.isPresent() && !phase.isBlank()) {
                indexes.dialogueByQuestPhase.put(questPhaseKey(questId.get(), phase), timing);
            }
        }

        for (CatalogRecord spawn : records(bundle.node(CatalogFile.MOB_SPAWN), mapper)) {
            spawn.intValue("mapId").ifPresent(mapId -> indexes.mobSpawnByMapId.put(mapId, spawn));
            for (CatalogRecord mob : spawn.recordList("mobs")) {
                mob.intValue("mobId").ifPresent(mobId -> add(indexes.mobSpawnMapsByMobId, mobId, spawn));
            }
        }

        indexes.indexSimple(bundle.node(CatalogFile.ITEM_SOURCES), mapper, "itemId", indexes.itemSourcesByItemId, null, null);
        indexes.indexReactors(bundle, mapper);
        indexes.indexMapleIsland(bundle, mapper);
        indexes.dropSourceClassifications.addAll(records(bundle.node(CatalogFile.DROP_SOURCE_CLASSIFICATIONS), mapper));
        indexes.victoriaQuestStatuses.addAll(records(bundle.node(CatalogFile.VICTORIA_LT30_QUEST_STATUS), mapper));
        return indexes.freeze();
    }

    private CatalogIndexes freeze() {
        freezeListMap(npcPlacementsByNpcId);
        freezeListMap(npcPlacementsByMapId);
        freezeListMap(npcActionsByNpcId);
        freezeListMap(npcActionsByQuestId);
        freezeListMap(portalEdgesByMapId);
        freezeListMap(mobSpawnMapsByMobId);
        freezeListMap(dropEntriesByItemId);
        freezeListMap(dropEntriesByMobId);
        freezeListMap(reactorsByMapId);
        freezeListMap(reactorsById);
        freezeListMap(reactorsByQuestId);
        freezeListMap(reactorsByDroppedItemId);
        return this;
    }

    private void indexReactors(CatalogBundle bundle, ObjectMapper mapper) {
        for (CatalogRecord reactor : records(bundle.node(CatalogFile.REACTORS), mapper)) {
            reactor.intValue("mapId").ifPresent(mapId -> add(reactorsByMapId, mapId, reactor));
            reactor.intValue("reactorId").ifPresent(reactorId -> add(reactorsById, reactorId, reactor));
            for (Integer questId : reactor.intList("inferredQuestIds")) {
                add(reactorsByQuestId, questId, reactor);
            }
            for (Integer itemId : reactor.intList("inferredItemIds")) {
                add(reactorsByDroppedItemId, itemId, reactor);
            }
            for (CatalogRecord drop : reactor.recordList("drops")) {
                drop.intValue("questId")
                        .filter(questId -> questId > 0)
                        .ifPresent(questId -> add(reactorsByQuestId, questId, reactor));
                drop.intValue("itemId")
                        .filter(itemId -> itemId > 0)
                        .ifPresent(itemId -> add(reactorsByDroppedItemId, itemId, reactor));
            }
        }
    }

    private void indexMapleIsland(CatalogBundle bundle, ObjectMapper mapper) {
        JsonNode mvp = bundle.node(CatalogFile.MAPLE_ISLAND_MVP);
        if (mvp != null) {
            for (CatalogRecord rule : records(mvp.path("quests"), mapper)) {
                rule.intValue("questId").ifPresent(questId -> mapleIslandQuestRuleByQuestId.put(questId, rule));
                for (CatalogRecord objective : rule.recordList("objectives")) {
                    objective.stringValue("objectiveId").ifPresent(id -> mapleIslandObjectiveById.put(id, objective));
                }
            }
            mapleIslandSpecialRules.addAll(records(mvp.path("specialRules"), mapper));
            mapleIslandForbiddenActions.addAll(records(mvp.path("forbiddenActions"), mapper));
        }

        JsonNode fast = bundle.node(CatalogFile.MAPLE_ISLAND_MVP_FAST_INDEXES);
        if (fast != null) {
            JsonNode byQuest = fast.path("questId_to_mvpRule");
            if (byQuest.isObject()) {
                byQuest.properties().forEach(entry -> {
                    CatalogRecord rule = CatalogRecord.from(entry.getValue(), mapper);
                    rule.intValue("questId").ifPresent(questId -> mapleIslandQuestRuleByQuestId.put(questId, rule));
                });
            }
            JsonNode byMap = fast.path("mapId_to_routeFacts");
            if (byMap.isObject()) {
                byMap.properties().forEach(entry -> {
                    CatalogRecord facts = CatalogRecord.from(entry.getValue(), mapper);
                    facts.intValue("mapId").ifPresent(mapId -> mapleIslandRouteFactsByMapId.put(mapId, facts));
                });
            }
        }
    }

    private void indexSimple(JsonNode node,
                             ObjectMapper mapper,
                             String idField,
                             Map<Integer, CatalogRecord> byId,
                             String nameField,
                             Map<String, List<CatalogRecord>> byName) {
        for (CatalogRecord record : records(node, mapper)) {
            record.intValue(idField).ifPresent(id -> byId.put(id, record));
            if (nameField != null && byName != null) {
                String name = record.lowerString(nameField);
                if (!name.isBlank()) {
                    add(byName, name, record);
                }
            }
        }
    }

    private void indexList(JsonNode node,
                           ObjectMapper mapper,
                           String idField,
                           Map<Integer, List<CatalogRecord>> target) {
        for (CatalogRecord record : records(node, mapper)) {
            record.intValue(idField).ifPresent(id -> add(target, id, record));
        }
    }

    static List<CatalogRecord> records(JsonNode node, ObjectMapper mapper) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        List<CatalogRecord> records = new ArrayList<>();
        if (node.isObject() && node.has("entries") && node.get("entries").isArray()) {
            node = node.get("entries");
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (child.isObject()) {
                    records.add(CatalogRecord.from(child, mapper));
                }
            }
        } else if (node.isObject()) {
            records.add(CatalogRecord.from(node, mapper));
        }
        return List.copyOf(records);
    }

    static String npcMapKey(int npcId, int mapId) {
        return npcId + "|" + mapId;
    }

    static String questPhaseKey(int questId, String phase) {
        return questId + "|" + phase.toLowerCase(Locale.ROOT);
    }

    static <K> void add(Map<K, List<CatalogRecord>> map, K key, CatalogRecord record) {
        map.computeIfAbsent(key, ignored -> new ArrayList<>()).add(record);
    }

    private static <K> void freezeListMap(Map<K, List<CatalogRecord>> map) {
        for (Map.Entry<K, List<CatalogRecord>> entry : new LinkedHashMap<>(map).entrySet()) {
            map.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
    }
}
