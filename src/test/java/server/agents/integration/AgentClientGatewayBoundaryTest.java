package server.agents.integration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentClientGatewayBoundaryTest {
    @Test
    void headlessClientImplementationLivesAtCosmicBoundary() throws IOException {
        String spawn = Files.readString(Path.of(
                "src/main/java/server/agents/commands/AgentSpawnCommandExecutor.java"));
        String offlineRuntime = Files.readString(Path.of(
                "src/main/java/server/agents/runtime/AgentOfflineLoadRuntime.java"));
        String offlineService = Files.readString(Path.of(
                "src/main/java/server/agents/runtime/AgentOfflineLoadService.java"));
        String cosmic = Files.readString(Path.of(
                "src/main/java/server/agents/integration/cosmic/CosmicAgentClientGateway.java"));

        assertFalse(spawn.contains("BotClient"));
        assertFalse(spawn.contains("BotCreator"));
        assertFalse(offlineRuntime.contains("BotClient"));
        assertFalse(offlineService.contains("BotClient"));
        assertTrue(spawn.contains("AgentClientGatewayRuntime.clients()"));
        assertTrue(offlineRuntime.contains("AgentClientGatewayRuntime.clients()"));
        assertTrue(cosmic.contains("new BotClient(world, channel)"));
        assertTrue(cosmic.contains("BotCreator.createCharacter(client, name)"));
    }
}
