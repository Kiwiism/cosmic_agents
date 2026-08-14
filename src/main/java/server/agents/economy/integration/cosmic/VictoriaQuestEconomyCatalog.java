package server.agents.economy.integration.cosmic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.economy.catalog.EconomyCatalog;
import server.agents.economy.catalog.MonsterDropFact;
import server.agents.economy.catalog.NpcLocationIndex;
import server.quest.Quest;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Immutable view of generator-approved Victoria quest facts; Cosmic WZ remains execution authority. */
public final class VictoriaQuestEconomyCatalog {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final List<Entry> entries;
    private final String catalogId;
    private final String revision;

    public VictoriaQuestEconomyCatalog(String resource, String requiredDisposition) {
        try (InputStream input = VictoriaQuestEconomyCatalog.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("missing Victoria quest catalog " + resource);
            JsonNode root = JSON.readTree(input);
            catalogId = requiredText(root, "catalogId");
            revision = requiredText(root, "revision");
            List<Entry> loaded = new ArrayList<>();
            for (JsonNode node : root.path("entries")) {
                if (!node.path("autonomousStartAllowed").asBoolean(false)
                        || !requiredDisposition.equals(node.path("selectionDisposition").asText())) continue;
                loaded.add(new Entry(node.path("questId").asInt(),
                        node.path("questName").asText(""), nullableInt(node, "minLevel"),
                        nullableInt(node, "maxLevel"), node.path("startNpcId").asInt(),
                        node.path("completeNpcId").asInt()));
            }
            loaded.sort(Comparator.comparingInt(Entry::questId));
            if (loaded.isEmpty()) throw new IllegalStateException("Victoria quest catalog has no eligible entries");
            entries = List.copyOf(loaded);
        } catch (IOException failure) {
            throw new IllegalStateException("could not load Victoria quest catalog " + resource, failure);
        }
    }

    public static VictoriaQuestEconomyCatalog fromCosmic(
            String policyResource, String requiredDisposition, String mapResource,
            NpcLocationIndex npcLocations, EconomyCatalog economy, Predicate<Quest> supported) {
        VictoriaQuestEconomyCatalog policy = new VictoriaQuestEconomyCatalog(
                policyResource, requiredDisposition);
        try (InputStream input = VictoriaQuestEconomyCatalog.class.getResourceAsStream(mapResource)) {
            if (input == null) throw new IllegalStateException("missing Victoria map catalog " + mapResource);
            JsonNode root = JSON.readTree(input);
            String mapRevision = requiredText(root, "revision");
            Set<Integer> maps = new HashSet<>();
            Set<Integer> mobs = new HashSet<>();
            for (JsonNode map : root.path("entries")) {
                maps.add(map.path("mapId").asInt());
                for (JsonNode mob : map.path("mobs")) mobs.add(mob.path("mobId").asInt());
            }
            Map<Integer, Set<Integer>> dropQuestIds = new HashMap<>();
            for (Integer mobId : mobs) {
                for (MonsterDropFact drop : economy.monsterDrops(mobId)) {
                    if (drop.itemId() > 0) dropQuestIds
                            .computeIfAbsent(drop.itemId(), ignored -> new HashSet<>())
                            .add(drop.questId());
                }
            }
            Map<Integer, Entry> merged = new LinkedHashMap<>();
            policy.entries.forEach(entry -> merged.put(entry.questId(), entry));
            for (Quest quest : Quest.allQuests()) {
                int startNpc = quest.getNpcRequirement(false);
                int completeNpc = quest.getNpcRequirement(true);
                if (startNpc <= 0 || completeNpc <= 0 || !supported.test(quest)
                        || !inVictoria(npcLocations.maps(startNpc), maps)
                        || !inVictoria(npcLocations.maps(completeNpc), maps)
                        || !mobs.containsAll(quest.getCompleteMobRequirements().keySet())
                        || !sourced(quest, dropQuestIds)) continue;
                merged.putIfAbsent((int) quest.getId(), new Entry(quest.getId(), quest.getName(),
                        null, null, startNpc, completeNpc));
            }
            return new VictoriaQuestEconomyCatalog(merged.values().stream()
                    .sorted(Comparator.comparingInt(Entry::questId)).toList(),
                    policy.catalogId + "+wz-victoria",
                    policy.revision + ':' + mapRevision + ':' + npcLocations.revision());
        } catch (IOException failure) {
            throw new IllegalStateException("could not load Victoria map catalog " + mapResource, failure);
        }
    }

    private VictoriaQuestEconomyCatalog(List<Entry> entries, String catalogId, String revision) {
        if (entries.isEmpty()) throw new IllegalArgumentException("quest catalog is empty");
        this.entries = List.copyOf(entries);
        this.catalogId = catalogId;
        this.revision = revision;
    }

    private static boolean inVictoria(List<Integer> npcMaps, Set<Integer> victoriaMaps) {
        return npcMaps.stream().anyMatch(victoriaMaps::contains);
    }

    private static boolean sourced(Quest quest, Map<Integer, Set<Integer>> dropQuestIds) {
        Map<Integer, Integer> required = new HashMap<>(quest.getStartItemRequirements());
        quest.getCompleteItemRequirements().forEach((item, quantity) ->
                required.merge(item, quantity, Math::max));
        for (Map.Entry<Integer, Integer> item : required.entrySet()) {
            if (item.getValue() <= 0) continue;
            Set<Integer> questIds = dropQuestIds.getOrDefault(item.getKey(), Set.of());
            if (!questIds.contains(0) && !questIds.contains((int) quest.getId())) return false;
        }
        return true;
    }

    public List<Entry> eligibleAtLevel(int level) {
        return entries.stream().filter(entry -> entry.levelEligible(level)).toList();
    }

    public java.util.Optional<Entry> find(int questId) {
        return entries.stream().filter(entry -> entry.questId() == questId).findFirst();
    }

    public String version() { return catalogId + ':' + revision; }

    private static Integer nullableInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private static String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) throw new IllegalStateException("quest catalog is missing " + field);
        return value;
    }

    public record Entry(int questId, String questName, Integer minimumLevel, Integer maximumLevel,
                        int startNpcId, int completeNpcId) {
        public Entry {
            if (questId <= 0 || startNpcId <= 0 || completeNpcId <= 0)
                throw new IllegalArgumentException("quest catalog entry requires IDs and NPCs");
            questName = questName == null ? "" : questName;
        }
        boolean levelEligible(int level) {
            return (minimumLevel == null || level >= minimumLevel)
                    && (maximumLevel == null || level <= maximumLevel);
        }
    }
}
