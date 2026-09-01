package server.agents.capabilities.partyquest.opq;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentOpqArchitectureBoundaryTest {
    @Test
    void coordinatorUsesAuthoredTravelAndRejectsRemoteInteractionShortcuts() throws Exception {
        Path root = Path.of("src/main/java/server/agents/capabilities/partyquest/opq");
        StringBuilder all = new StringBuilder();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                all.append(Files.readString(file));
            }
        }
        String source = all.toString();
        assertFalse(source.contains("changeMap("));
        assertFalse(source.contains("changeMapNear("));
        assertFalse(source.contains("setPosition("));
        assertFalse(source.contains("stagePosition("));
        assertFalse(source.contains("recoverToMap("));
        assertFalse(source.contains("forceHitReactor"));
        assertTrue(source.contains("AgentOpqInteractionPolicy.mayHitReactor"));
        assertTrue(source.contains("enterAuthoredPortal"));
    }
}
