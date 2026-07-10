package server.agents.integration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPersistenceGatewayBoundaryTest {
    @Test
    void databaseOperationsLiveAtCosmicBoundary() throws IOException {
        String command = Files.readString(Path.of(
                "src/main/java/server/agents/commands/AgentSpawnCommandExecutor.java"));
        String ownership = Files.readString(Path.of(
                "src/main/java/server/agents/auth/AgentOwnershipService.java"));
        String cosmic = Files.readString(Path.of(
                "src/main/java/server/agents/integration/cosmic/CosmicAgentPersistenceGateway.java"));

        assertFalse(command.contains("tools.DatabaseConnection"));
        assertFalse(ownership.contains("tools.DatabaseConnection"));
        assertTrue(command.contains("AgentPersistenceGatewayRuntime.persistence()"));
        assertTrue(ownership.contains("AgentPersistenceGatewayRuntime.persistence()"));
        assertTrue(cosmic.contains("tools.DatabaseConnection"));
        assertTrue(cosmic.contains("bot_owners"));
    }
}
