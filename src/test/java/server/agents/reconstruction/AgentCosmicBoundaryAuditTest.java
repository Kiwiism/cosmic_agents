package server.agents.reconstruction;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentCosmicBoundaryAuditTest {
    private static final Path AGENTS = Path.of("src", "main", "java", "server", "agents");
    private static final List<String> FORBIDDEN_OUTSIDE_COSMIC = List.of(
            "server.bots",
            "DatabaseConnection",
            ".getClient()",
            "saveCharToDB",
            "PacketCreator",
            "ByteBufInPacket",
            "ByteBufOutPacket",
            "import net.opcodes.",
            "UseItemHandler",
            "ShopFactory",
            "TimerManager");

    @Test
    void operationalCosmicDependenciesRemainInsideCosmicAdapters() throws Exception {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(AGENTS)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".java")).toList()) {
                String normalized = path.toString().replace('\\', '/');
                if (normalized.contains("/integration/cosmic/")) {
                    continue;
                }
                String source = Files.readString(path);
                for (String forbidden : FORBIDDEN_OUTSIDE_COSMIC) {
                    if (source.contains(forbidden)) {
                        violations.add(normalized + " contains " + forbidden);
                    }
                }
            }
        }
        assertEquals(List.of(), violations);
    }

    @Test
    void directClientTypeIsLimitedToTheCompatibilityGatewayAndSpawnCommandAdapter() throws Exception {
        Set<String> allowed = Set.of(
                "src/main/java/server/agents/commands/AgentSpawnCommandExecutor.java",
                "src/main/java/server/agents/integration/AgentClientGateway.java");
        List<String> actual = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(AGENTS)) {
            paths.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> {
                        try {
                            return Files.readString(file).contains("import client.Client;");
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .map(path -> path.toString().replace('\\', '/'))
                    .filter(path -> !path.contains("/integration/cosmic/"))
                    .forEach(actual::add);
        }
        assertEquals(allowed, Set.copyOf(actual));
    }

    @Test
    void productionBotPackageRemainsAbsent() {
        assertFalse(Files.exists(Path.of("src", "main", "java", "server", "bots")));
        assertFalse(Files.exists(Path.of("src", "test", "java", "server", "bots")));
    }
}
