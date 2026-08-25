package server.agents.economy.activity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.economy.scenario.EconomyConfigException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only generated quest-objective map ranking used by external hunt planning. */
public final class QuestObjectiveMapIndex {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Map<Integer, List<Integer>> byQuest;

    public QuestObjectiveMapIndex(String resource) { this.byQuest = load(resource); }

    public List<Integer> preferredMaps(Set<Integer> activeQuestIds) {
        LinkedHashSet<Integer> ordered = new LinkedHashSet<>();
        activeQuestIds.stream().sorted().forEach(questId ->
                ordered.addAll(byQuest.getOrDefault(questId, List.of())));
        return List.copyOf(ordered);
    }

    private static Map<Integer, List<Integer>> load(String resource) {
        try (InputStream input = QuestObjectiveMapIndex.class.getResourceAsStream(resource)) {
            if (input == null) throw new EconomyConfigException("Missing quest hunt index " + resource);
            JsonNode root = JSON.readTree(input);
            if (root.path("schemaVersion").asInt() != 2 || root.path("revision").asText().isBlank())
                throw new EconomyConfigException("Unversioned quest hunt index " + resource);
            Map<Integer, List<Integer>> result = new LinkedHashMap<>();
            for (JsonNode entry : root.path("entries")) {
                List<Integer> maps = new ArrayList<>();
                for (JsonNode candidate : entry.path("combinedCandidates"))
                    maps.add(candidate.path("mapId").asInt());
                result.put(entry.path("questId").asInt(), List.copyOf(maps));
            }
            return Map.copyOf(result);
        } catch (IOException failure) {
            throw new EconomyConfigException("Could not read quest hunt index " + resource, failure);
        }
    }
}
