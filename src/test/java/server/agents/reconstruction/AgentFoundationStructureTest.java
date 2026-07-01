package server.agents.reconstruction;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFoundationStructureTest {
    @Test
    void foundationClassesExist() throws Exception {
        List<String> classes = List.of(
                "server.agents.api.AgentService",
                "server.agents.runtime.AgentSession",
                "server.agents.runtime.AgentPartyLifecycleService",
                "server.agents.runtime.AgentTickScheduler",
                "server.agents.commands.AgentCommandRouter",
                "server.agents.commands.AgentCommandParser",
                "server.agents.plans.AgentPlanRunner",
                "server.agents.capabilities.AgentCapability",
                "server.agents.events.AgentEventBus",
                "server.agents.policy.AgentCombatPolicy",
                "server.agents.profiles.AgentProfileRepository",
                "server.agents.legacy.LegacyBotParityAdapter",
                "server.agents.integration.AgentServerAdapter",
                "server.agents.integration.cosmic.CosmicAgentServerAdapter");

        for (String className : classes) {
            Class.forName(className);
        }
    }

    @Test
    void currentBotProductionFilesAreMapped() throws IOException {
        Path map = Path.of("docs", "agents", "BOT_TO_AGENT_RECONSTRUCTION_MAP.md");
        String mapping = Files.readString(map).replace('\\', '/');
        List<Path> botFiles;
        try (var stream = Files.walk(Path.of("src", "main", "java", "server", "bots"))) {
            botFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }

        assertFalse(botFiles.isEmpty(), "source baseline should still contain bot files during reconstruction");
        for (Path file : botFiles) {
            String normalized = file.toString().replace('\\', '/');
            assertTrue(mapping.contains(normalized), "missing reconstruction map entry for " + normalized);
        }
    }
}
