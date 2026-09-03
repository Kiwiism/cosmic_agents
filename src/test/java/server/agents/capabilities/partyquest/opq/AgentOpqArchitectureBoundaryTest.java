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
            for (Path file : files.filter(path -> path.toString().endsWith(".java"))
                    // The GM-only harness may place a fully prepared fixture at recruitment
                    // before a session exists. It is not an OPQ navigation owner.
                    .filter(path -> !path.getFileName().toString().equals("AgentOpqTestService.java"))
                    .toList()) {
                all.append(Files.readString(file));
            }
        }
        String source = all.toString();
        assertFalse(source.contains("changeMap("));
        assertFalse(source.contains("changeMapNear("));
        assertFalse(source.contains("setPosition("));
        assertFalse(source.contains("stagePosition("));
        assertFalse(source.contains("teleportTo("));
        assertFalse(source.contains("flyTo("));
        assertFalse(source.contains("recoverToMap("));
        assertFalse(source.contains("forceHitReactor"));
        assertFalse(source.contains("attackMonster("));
        assertFalse(source.contains("applyAttackRoute("));
        assertTrue(source.contains("AgentOpqInteractionPolicy.mayHitReactor"));
        assertTrue(source.contains("enterAuthoredPortal"));
    }

    @Test
    void testFixtureRelocationEndsBeforeSessionOwnershipBegins() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/server/agents/capabilities/partyquest/opq/AgentOpqTestService.java"));
        int fixtureReturn = source.indexOf(".changeMapNear(");
        int sessionRegistration = source.indexOf("AgentOpqSessionRegistry.registerComplete(");
        assertTrue(fixtureReturn >= 0);
        assertTrue(sessionRegistration > fixtureReturn);
        assertFalse(source.contains("setPosition("));
        assertFalse(source.contains("teleportTo("));
        assertFalse(source.contains("flyTo("));
        assertFalse(source.contains("attackMonster("));
    }
}
