package server.agents.runtime.async;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentSchedulerBlockingBoundaryTest {
    private static final Path AGENT_SOURCE = Path.of("src/main/java/server/agents");
    private static final List<Pattern> BLOCKING_PATTERNS = List.of(
            Pattern.compile("\\.join\\s*\\(\\s*\\)"),
            Pattern.compile("Thread\\.sleep\\s*\\("),
            Pattern.compile("\\.get\\s*\\([^)]*TimeUnit"));

    @Test
    void agentProductionCodeContainsNoDirectWaitPrimitive() throws Exception {
        try (var files = Files.walk(AGENT_SOURCE)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (Pattern pattern : BLOCKING_PATTERNS) {
                    assertFalse(pattern.matcher(source).find(),
                            file + " contains blocking pattern " + pattern.pattern());
                }
            }
        }
    }

    @Test
    void synchronousNavigationGraphAccessIsRestrictedToExplicitTools() throws Exception {
        try (var files = Files.walk(AGENT_SOURCE)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String name = file.getFileName().toString();
                if (name.equals("AgentNavigationGraphService.java")
                        || name.equals("AgentNavigationDebugOverlay.java")
                        || name.equals("AgentNavigationProbe.java")) {
                    continue;
                }
                assertFalse(
                        Files.readString(file).contains("AgentNavigationGraphService.getGraph("),
                        file + " may block on synchronous graph construction");
            }
        }
    }
}
