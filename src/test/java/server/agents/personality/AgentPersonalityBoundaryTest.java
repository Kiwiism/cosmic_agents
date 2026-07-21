package server.agents.personality;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentPersonalityBoundaryTest {
    private static final Path PERSONALITY = Path.of(
            "src", "main", "java", "server", "agents", "personality");
    private static final Path PRESENTATION = Path.of(
            "src", "main", "java", "server", "agents", "capabilities", "presentation");

    @Test
    void durablePersonalityDoesNotDependOnLiveClientTypes() throws Exception {
        assertSourceDoesNotContain(PERSONALITY, "import client.");
    }

    @Test
    void reusablePresentationDoesNotDependOnConfigOrSpecificPlans() throws Exception {
        assertSourceDoesNotContain(PRESENTATION, "import config.");
        assertSourceDoesNotContain(PRESENTATION, "import server.agents.plans.");
    }

    private static void assertSourceDoesNotContain(Path root, String forbidden) throws Exception {
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains(forbidden),
                        () -> file + " crosses the personality module boundary with " + forbidden);
            }
        }
    }
}
