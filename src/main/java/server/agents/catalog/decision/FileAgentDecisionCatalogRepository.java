package server.agents.catalog.decision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.catalog.CatalogLookupException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads the navigation and combat decision catalogs as one validated immutable snapshot. */
public final class FileAgentDecisionCatalogRepository implements AgentDecisionCatalogRepository {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final String MANIFEST_FILE = "generated_agent_decision_catalog_manifest.json";
    private static final String NAVIGATION_FILE = "generated_navigation_topology_catalog.json";
    private static final String COMBAT_FILE = "generated_combat_map_policy_catalog.json";

    private final AgentDecisionCatalogSnapshot snapshot;

    private FileAgentDecisionCatalogRepository(AgentDecisionCatalogSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public static FileAgentDecisionCatalogRepository load(Path catalogDirectory) {
        if (catalogDirectory == null) {
            throw new IllegalArgumentException("Decision catalog directory is required");
        }
        Path directory = catalogDirectory.toAbsolutePath().normalize();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode manifest = read(mapper, directory.resolve(MANIFEST_FILE));
        int schemaVersion = requiredInt(manifest, "schemaVersion", MANIFEST_FILE);
        if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new CatalogLookupException("Unsupported decision catalog schemaVersion " + schemaVersion
                    + "; expected " + SUPPORTED_SCHEMA_VERSION);
        }
        if (manifest.path("partialExport").asBoolean(false)) {
            throw new CatalogLookupException("Partial decision catalogs cannot be loaded by the Agent runtime");
        }

        Map<String, Integer> counts = integerFields(manifest.path("counts"));
        AgentDecisionCatalogSnapshot.Version version = new AgentDecisionCatalogSnapshot.Version(
                schemaVersion,
                requiredText(manifest, "generatedAt", MANIFEST_FILE),
                manifest.path("allRegions").asBoolean(false),
                textValues(manifest.path("regions")),
                counts);

        Map<Integer, AgentDecisionCatalogSnapshot.MapTopology> navigation = parseNavigation(
                read(mapper, directory.resolve(NAVIGATION_FILE)), schemaVersion);
        Map<Integer, AgentDecisionCatalogSnapshot.CombatMapPolicy> combat = parseCombat(
                read(mapper, directory.resolve(COMBAT_FILE)), schemaVersion, navigation);

        requireCount(counts, "navigationMaps", navigation.size());
        requireCount(counts, "combatMaps", combat.size());
        return new FileAgentDecisionCatalogRepository(
                new AgentDecisionCatalogSnapshot(version, navigation, combat));
    }

    @Override
    public AgentDecisionCatalogSnapshot snapshot() {
        return snapshot;
    }

    private static Map<Integer, AgentDecisionCatalogSnapshot.MapTopology> parseNavigation(
            JsonNode root,
            int schemaVersion) {
        if (!root.isArray()) {
            throw new CatalogLookupException(NAVIGATION_FILE + " must contain a JSON array");
        }
        Map<Integer, AgentDecisionCatalogSnapshot.MapTopology> maps = new LinkedHashMap<>();
        for (JsonNode node : root) {
            requireSchema(node, schemaVersion, NAVIGATION_FILE);
            int mapId = requiredInt(node, "mapId", NAVIGATION_FILE);
            Map<Integer, AgentDecisionCatalogSnapshot.Foothold> footholds = new LinkedHashMap<>();
            for (JsonNode footholdNode : array(node, "footholds", NAVIGATION_FILE)) {
                AgentDecisionCatalogSnapshot.Foothold foothold = new AgentDecisionCatalogSnapshot.Foothold(
                        requiredInt(footholdNode, "footholdId", NAVIGATION_FILE),
                        requiredInt(footholdNode, "componentId", NAVIGATION_FILE),
                        requiredInt(footholdNode, "x1", NAVIGATION_FILE),
                        requiredInt(footholdNode, "y1", NAVIGATION_FILE),
                        requiredInt(footholdNode, "x2", NAVIGATION_FILE),
                        requiredInt(footholdNode, "y2", NAVIGATION_FILE),
                        footholdNode.path("wall").asBoolean(false));
                putUnique(footholds, foothold.footholdId(), foothold, "foothold", mapId);
            }

            Map<Integer, AgentDecisionCatalogSnapshot.Component> components = new LinkedHashMap<>();
            for (JsonNode componentNode : array(node, "components", NAVIGATION_FILE)) {
                int componentId = requiredInt(componentNode, "componentId", NAVIGATION_FILE);
                JsonNode bounds = requiredObject(componentNode, "bounds", NAVIGATION_FILE);
                JsonNode center = requiredObject(componentNode, "center", NAVIGATION_FILE);
                JsonNode safePoint = componentNode.path("safePoint");
                AgentDecisionCatalogSnapshot.Component component = new AgentDecisionCatalogSnapshot.Component(
                        componentId,
                        new AgentDecisionCatalogSnapshot.Bounds(
                                requiredInt(bounds, "minX", NAVIGATION_FILE),
                                requiredInt(bounds, "maxX", NAVIGATION_FILE),
                                requiredInt(bounds, "minY", NAVIGATION_FILE),
                                requiredInt(bounds, "maxY", NAVIGATION_FILE)),
                        point(center, NAVIGATION_FILE),
                        safePoint.isObject() ? point(safePoint, NAVIGATION_FILE) : null,
                        intValues(componentNode.path("footholdIds")));
                putUnique(components, componentId, component, "component", mapId);
            }
            for (AgentDecisionCatalogSnapshot.Foothold foothold : footholds.values()) {
                if (!components.containsKey(foothold.componentId())) {
                    throw new CatalogLookupException("Map " + mapId + " foothold " + foothold.footholdId()
                            + " references missing component " + foothold.componentId());
                }
            }
            for (AgentDecisionCatalogSnapshot.Component component : components.values()) {
                for (int footholdId : component.footholdIds()) {
                    AgentDecisionCatalogSnapshot.Foothold foothold = footholds.get(footholdId);
                    if (foothold == null || foothold.componentId() != component.componentId()) {
                        throw new CatalogLookupException("Map " + mapId + " component "
                                + component.componentId() + " references invalid foothold " + footholdId);
                    }
                }
            }

            validateMovementAuthority(node, mapId);
            List<AgentDecisionCatalogSnapshot.Transition> transitions = new ArrayList<>();
            for (JsonNode transition : array(node, "transitionCandidates", NAVIGATION_FILE)) {
                String type = requiredText(transition, "type", NAVIGATION_FILE);
                boolean jump = "jump-candidate".equals(type);
                int from = requiredInt(transition, jump ? "componentA" : "fromComponentId", NAVIGATION_FILE);
                int to = requiredInt(transition, jump ? "componentB" : "toComponentId", NAVIGATION_FILE);
                if (!components.containsKey(from) || !components.containsKey(to)) {
                    throw new CatalogLookupException("Map " + mapId + " transition references a missing component");
                }
                transitions.add(new AgentDecisionCatalogSnapshot.Transition(type, from, to, jump));
            }

            AgentDecisionCatalogSnapshot.MapTopology topology = new AgentDecisionCatalogSnapshot.MapTopology(
                    mapId,
                    node.path("mapName").asText(""),
                    footholds,
                    components,
                    transitions);
            putUnique(maps, mapId, topology, "navigation map", mapId);
        }
        return Map.copyOf(maps);
    }

    private static Map<Integer, AgentDecisionCatalogSnapshot.CombatMapPolicy> parseCombat(
            JsonNode root,
            int schemaVersion,
            Map<Integer, AgentDecisionCatalogSnapshot.MapTopology> navigation) {
        if (!root.isArray()) {
            throw new CatalogLookupException(COMBAT_FILE + " must contain a JSON array");
        }
        Map<Integer, AgentDecisionCatalogSnapshot.CombatMapPolicy> policies = new LinkedHashMap<>();
        for (JsonNode node : root) {
            requireSchema(node, schemaVersion, COMBAT_FILE);
            int mapId = requiredInt(node, "mapId", COMBAT_FILE);
            AgentDecisionCatalogSnapshot.MapTopology topology = navigation.get(mapId);
            if (topology == null) {
                throw new CatalogLookupException("Combat policy map " + mapId + " has no navigation topology");
            }
            if (!node.path("policy").path("anchorReachabilityRequiresRuntimeValidation").asBoolean(false)) {
                throw new CatalogLookupException("Combat policy map " + mapId
                        + " does not preserve runtime reachability authority");
            }

            Map<String, AgentDecisionCatalogSnapshot.CombatAnchor> anchors = new LinkedHashMap<>();
            for (JsonNode anchorNode : array(node, "anchors", COMBAT_FILE)) {
                String anchorId = requiredText(anchorNode, "anchorId", COMBAT_FILE);
                int componentId = requiredInt(anchorNode, "componentId", COMBAT_FILE);
                if (!topology.componentsById().containsKey(componentId)) {
                    throw new CatalogLookupException("Combat anchor " + anchorId
                            + " references missing component " + componentId);
                }
                AgentDecisionCatalogSnapshot.CombatAnchor anchor = new AgentDecisionCatalogSnapshot.CombatAnchor(
                        anchorId,
                        componentId,
                        point(requiredObject(anchorNode, "center", COMBAT_FILE), COMBAT_FILE),
                        requiredInt(anchorNode, "spawnCount", COMBAT_FILE),
                        new HashSet<>(intValues(anchorNode.path("mobIds"))));
                if (anchors.putIfAbsent(anchorId, anchor) != null) {
                    throw new CatalogLookupException("Duplicate combat anchor " + anchorId);
                }
            }

            Map<Integer, AgentDecisionCatalogSnapshot.CombatPartition> partitions = new LinkedHashMap<>();
            for (JsonNode partitionNode : array(node, "partyPartitions", COMBAT_FILE)) {
                int partySize = requiredInt(partitionNode, "partySize", COMBAT_FILE);
                List<AgentDecisionCatalogSnapshot.PartitionGroup> groups = new ArrayList<>();
                for (JsonNode groupNode : array(partitionNode, "groups", COMBAT_FILE)) {
                    List<String> anchorIds = textValues(groupNode.path("anchorIds"));
                    if (!anchors.keySet().containsAll(anchorIds)) {
                        throw new CatalogLookupException("Combat partition for map " + mapId
                                + " references an unknown anchor");
                    }
                    groups.add(new AgentDecisionCatalogSnapshot.PartitionGroup(
                            requiredInt(groupNode, "slot", COMBAT_FILE), anchorIds));
                }
                AgentDecisionCatalogSnapshot.CombatPartition partition =
                        new AgentDecisionCatalogSnapshot.CombatPartition(partySize, groups);
                putUnique(partitions, partySize, partition, "party partition", mapId);
            }

            AgentDecisionCatalogSnapshot.CombatMapPolicy policy =
                    new AgentDecisionCatalogSnapshot.CombatMapPolicy(
                            mapId,
                            requiredInt(node, "recommendedAgents", COMBAT_FILE),
                            requiredInt(node, "maximumAgents", COMBAT_FILE),
                            anchors,
                            partitions);
            putUnique(policies, mapId, policy, "combat policy", mapId);
        }
        return Map.copyOf(policies);
    }

    private static void validateMovementAuthority(JsonNode map, int mapId) {
        if (!map.path("policy").path("runtimeMustValidateMovement").asBoolean(false)) {
            throw new CatalogLookupException("Map " + mapId + " does not preserve runtime movement authority");
        }
        for (JsonNode climb : array(map, "climbables", NAVIGATION_FILE)) {
            if (climb.path("executable").asBoolean(false)
                    || !climb.path("requiresRuntimePhysicsValidation").asBoolean(false)) {
                throw new CatalogLookupException("Map " + mapId + " contains an authoritative climb hint");
            }
        }
        for (JsonNode transition : array(map, "transitionCandidates", NAVIGATION_FILE)) {
            if (transition.path("executable").asBoolean(false)
                    || !transition.path("requiresRuntimePhysicsValidation").asBoolean(false)) {
                throw new CatalogLookupException("Map " + mapId + " contains an authoritative transition hint");
            }
        }
    }

    private static JsonNode read(ObjectMapper mapper, Path path) {
        if (!Files.isRegularFile(path)) {
            throw new CatalogLookupException("Missing required decision catalog file: " + path);
        }
        try {
            return mapper.readTree(path.toFile());
        } catch (IOException failure) {
            throw new CatalogLookupException("Failed to read decision catalog JSON: " + path, failure);
        }
    }

    private static JsonNode requiredObject(JsonNode node, String field, String source) {
        JsonNode value = node.path(field);
        if (!value.isObject()) {
            throw new CatalogLookupException(source + " requires object field '" + field + "'");
        }
        return value;
    }

    private static Iterable<JsonNode> array(JsonNode node, String field, String source) {
        JsonNode value = node.path(field);
        if (!value.isArray()) {
            throw new CatalogLookupException(source + " requires array field '" + field + "'");
        }
        return value;
    }

    private static int requiredInt(JsonNode node, String field, String source) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber()) {
            throw new CatalogLookupException(source + " requires integer field '" + field + "'");
        }
        return value.intValue();
    }

    private static String requiredText(JsonNode node, String field, String source) {
        String value = node.path(field).asText("").trim();
        if (value.isEmpty()) {
            throw new CatalogLookupException(source + " requires text field '" + field + "'");
        }
        return value;
    }

    private static void requireSchema(JsonNode node, int schemaVersion, String source) {
        if (requiredInt(node, "schemaVersion", source) != schemaVersion) {
            throw new CatalogLookupException(source + " contains a record with a mismatched schemaVersion");
        }
    }

    private static List<Integer> intValues(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<Integer> values = new ArrayList<>();
        for (JsonNode value : node) {
            if (value.isIntegralNumber()) {
                values.add(value.intValue());
            }
        }
        return List.copyOf(values);
    }

    private static List<String> textValues(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) {
            String text = value.asText("").trim();
            if (!text.isEmpty()) {
                values.add(text);
            }
        }
        return List.copyOf(values);
    }

    private static Map<String, Integer> integerFields(JsonNode node) {
        if (!node.isObject()) {
            throw new CatalogLookupException(MANIFEST_FILE + " requires object field 'counts'");
        }
        Map<String, Integer> values = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getValue().isIntegralNumber()) {
                values.put(field.getKey(), field.getValue().intValue());
            }
        }
        return Map.copyOf(values);
    }

    private static AgentDecisionCatalogSnapshot.Point point(JsonNode node, String source) {
        return new AgentDecisionCatalogSnapshot.Point(
                requiredInt(node, "x", source),
                requiredInt(node, "y", source));
    }

    private static void requireCount(Map<String, Integer> counts, String key, int actual) {
        Integer expected = counts.get(key);
        if (expected == null || expected != actual) {
            throw new CatalogLookupException("Decision catalog count mismatch for " + key
                    + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static <K, V> void putUnique(Map<K, V> target, K key, V value, String kind, int mapId) {
        if (target.putIfAbsent(key, value) != null) {
            throw new CatalogLookupException("Duplicate " + kind + " key " + key + " in map " + mapId);
        }
    }
}
