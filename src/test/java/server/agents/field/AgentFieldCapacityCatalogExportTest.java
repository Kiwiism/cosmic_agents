package server.agents.field;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Manual deterministic exporter; production consumes its checked-in snapshot. */
class AgentFieldCapacityCatalogExportTest {
    private static final Path MAP_FACTS = Path.of(
            "src/main/resources/agents/catalogs/adaptive/victoria-map-facts.json");
    private static final Path MANIFEST = Path.of(
            "src/main/resources/agents/field/victoria-level15-25-observation-harness.json");
    private static final Path OVERRIDES = Path.of(
            "src/main/resources/agents/field/victoria-field-capacity-overrides.json");
    private static final Path OUTPUT = Path.of(
            "src/main/resources/agents/field/victoria-field-capacities.json");

    @Test
    @EnabledIfSystemProperty(named = "agent.field.exportCapacity", matches = "true")
    void exportsObservationCapacityProfiles() throws Exception {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        JsonNode factsRoot = mapper.readTree(MAP_FACTS.toFile());
        JsonNode manifestRoot = mapper.readTree(MANIFEST.toFile());
        JsonNode overridesRoot = mapper.readTree(OVERRIDES.toFile());

        Map<Integer, JsonNode> facts = index(factsRoot.path("entries"));
        Map<Integer, AgentFieldCapacityEstimator.CapacityOverride> overrides = overrides(overridesRoot);
        ArrayList<AgentFieldCapacityCatalog.MapCapacity> capacities = new ArrayList<>();
        for (JsonNode map : manifestRoot.path("maps")) {
            int mapId = map.path("mapId").asInt();
            JsonNode fact = facts.get(mapId);
            if (fact == null) {
                throw new IllegalStateException("missing map facts for observation map " + mapId);
            }
            ArrayList<AgentFieldCapacityEstimator.SpawnEvidence> spawns = new ArrayList<>();
            for (JsonNode spawn : fact.path("spawnPoints")) {
                spawns.add(new AgentFieldCapacityEstimator.SpawnEvidence(
                        spawn.path("componentId").asInt(), spawn.path("x").asInt(),
                        spawn.path("y").asInt()));
            }
            JsonNode topology = fact.path("topology");
            capacities.add(AgentFieldCapacityEstimator.estimate(
                    new AgentFieldCapacityEstimator.MapEvidence(
                            mapId, map.path("mapName").asText(),
                            fact.path("totalSpawnEntries").asInt(),
                            topology.path("climbableCount").asInt(),
                            topology.path("complexity").asText(), spawns),
                    overrides.get(mapId)));
        }
        assertFalse(capacities.isEmpty());
        AgentFieldCapacityCatalog catalog = new AgentFieldCapacityCatalog(
                1, "platform-capacity-v1", factsRoot.path("revision").asText(),
                new AgentFieldCapacityCatalog.Policy(
                        AgentFieldCapacityEstimator.AGENT_SPACING_PX,
                        AgentFieldCapacityEstimator.SPAWN_ENTRIES_PER_AGENT,
                        AgentFieldCapacityEstimator.FRAGMENTATION_PENALTY_PERCENT,
                        AgentFieldCapacityEstimator.MINIMUM_ACTIVE_PERCENT),
                capacities);
        Files.writeString(OUTPUT, mapper.writeValueAsString(catalog) + System.lineSeparator());
    }

    private static Map<Integer, JsonNode> index(JsonNode entries) {
        LinkedHashMap<Integer, JsonNode> indexed = new LinkedHashMap<>();
        for (JsonNode entry : entries) {
            indexed.put(entry.path("mapId").asInt(), entry);
        }
        return Map.copyOf(indexed);
    }

    private static Map<Integer, AgentFieldCapacityEstimator.CapacityOverride> overrides(JsonNode root) {
        HashMap<Integer, AgentFieldCapacityEstimator.CapacityOverride> indexed = new HashMap<>();
        for (JsonNode entry : root.path("maps")) {
            indexed.put(entry.path("mapId").asInt(), new AgentFieldCapacityEstimator.CapacityOverride(
                    nullableInt(entry, "recommendedMinimum"),
                    nullableInt(entry, "recommendedMaximum"),
                    nullableInt(entry, "maximumAgents"),
                    entry.path("confidence").asText("high"),
                    entry.path("reason").asText("observed capacity override")));
        }
        return Map.copyOf(indexed);
    }

    private static Integer nullableInt(JsonNode node, String field) {
        return node.has(field) && node.path(field).isInt() ? node.path(field).asInt() : null;
    }
}
