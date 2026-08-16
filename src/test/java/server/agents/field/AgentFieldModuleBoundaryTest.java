package server.agents.field;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentFieldModuleBoundaryTest {
    @Test
    void combatCapabilityDoesNotDependOnFieldCoordinator() throws Exception {
        Path combat = Path.of("src/main/java/server/agents/capabilities/combat");
        try (var files = Files.walk(combat)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("import server.agents.field."),
                        () -> file + " makes generic combat depend on field coordination");
            }
        }
    }

    @Test
    void fieldRuntimeDoesNotOwnConcreteAttackExecution() throws Exception {
        Path field = Path.of("src/main/java/server/agents/field");
        try (var files = Files.walk(field)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("AgentAttackExecutionProvider"),
                        () -> file + " makes the field allocator execute attacks");
                assertFalse(source.contains("AgentSkillAttackPlanner"),
                        () -> file + " makes the field allocator select skills");
            }
        }
    }
}
