package server.agents.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSystemOwnershipManifestTest {
    private static final Path SOURCE = Path.of("src", "main", "java");
    private static final Path AGENTS = SOURCE.resolve(Path.of("server", "agents"));
    private static final Path MANIFEST = Path.of("src", "main", "resources", "agents",
            "architecture", "system-ownership.json");

    @Test
    void everyAgentSourceAndEntrypointHasOneDeclaredOwner() throws IOException {
        JsonNode root = new ObjectMapper().readTree(MANIFEST.toFile());
        assertEquals(2, root.path("schemaVersion").asInt());
        JsonNode systems = root.path("systems");
        assertTrue(systems.isArray());

        Set<String> ids = new HashSet<>();
        Set<String> activityKinds = new HashSet<>();
        List<Owner> owners = new ArrayList<>();
        Map<String, JsonNode> declaredSystems = new LinkedHashMap<>();
        for (JsonNode system : systems) {
            String id = required(system, "id");
            assertTrue(ids.add(id), () -> "duplicate Agent system id: " + id);
            declaredSystems.put(id, system);
            String role = required(system, "role");
            if ("PRIMARY".equals(role)) {
                String kind = required(system, "activityKind");
                assertTrue(activityKinds.add(kind),
                        () -> "duplicate primary activity kind: " + kind);
            }
            for (JsonNode sourceRoot : system.path("sourceRoots")) {
                String normalized = normalize(sourceRoot.asText());
                assertTrue(Files.isDirectory(SOURCE.resolve(normalized)),
                        () -> "declared Agent source root does not exist: " + normalized);
                owners.add(new Owner(id, normalized));
            }
            for (JsonNode entryPoint : system.path("entryPoints")) {
                String normalized = normalize(entryPoint.asText());
                assertTrue(Files.isRegularFile(SOURCE.resolve(normalized)),
                        () -> "declared Agent entry point does not exist: " + normalized);
            }
            assertTrue(system.path("responsibilities").isArray()
                            && !system.path("responsibilities").isEmpty(),
                    () -> "system responsibilities are required: " + id);
        }

        assertEquals(Set.of("TOWN_LIFE", "HUNTING", "QUESTING", "COMMERCE", "PARTY_QUEST"),
                activityKinds);
        for (Map.Entry<String, JsonNode> declared : declaredSystems.entrySet()) {
            JsonNode collaborators = declared.getValue().path("collaborators");
            assertTrue(collaborators.isArray(),
                    () -> "system collaborators are required: " + declared.getKey());
            for (JsonNode collaborator : collaborators) {
                String collaboratorId = collaborator.asText("").trim();
                assertTrue(declaredSystems.containsKey(collaboratorId),
                        () -> declared.getKey() + " names unknown collaborator " + collaboratorId);
                assertFalse(declared.getKey().equals(collaboratorId),
                        () -> declared.getKey() + " cannot collaborate with itself");
            }
        }
        try (var files = Files.walk(AGENTS)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                String relative = normalize(SOURCE.relativize(path).toString());
                int longest = owners.stream()
                        .filter(owner -> matches(relative, owner.sourceRoot()))
                        .mapToInt(owner -> owner.sourceRoot().length()).max().orElse(-1);
                assertTrue(longest >= 0, () -> "unowned Agent source: " + relative);
                Set<String> bestOwners = new HashSet<>();
                for (Owner owner : owners) {
                    if (owner.sourceRoot().length() == longest
                            && matches(relative, owner.sourceRoot())) {
                        bestOwners.add(owner.id());
                    }
                }
                assertEquals(1, bestOwners.size(),
                        () -> "ambiguous Agent source ownership: " + relative + " -> " + bestOwners);
            });
        }
    }

    @Test
    void sourceTreeContainsNoTemporaryPatchArtifacts() throws IOException {
        try (var files = Files.walk(AGENTS)) {
            List<Path> artifacts = files.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(".orig") || name.endsWith(".rej")
                                || name.endsWith(".bak") || name.endsWith("~");
                    }).toList();
            assertTrue(artifacts.isEmpty(), () -> "temporary Agent source artifacts: " + artifacts);
        }
        assertFalse(Files.exists(Path.of("src", "main", "java", "server", "agents", "legacy")),
                "empty or revived Agent legacy package must not return");
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        assertFalse(value.isEmpty(), () -> "system manifest field is required: " + field);
        return value;
    }

    private static boolean matches(String file, String root) {
        return file.equals(root) || file.startsWith(root + "/");
    }

    private static String normalize(String value) {
        return value.replace('\\', '/');
    }

    private record Owner(String id, String sourceRoot) { }
}
