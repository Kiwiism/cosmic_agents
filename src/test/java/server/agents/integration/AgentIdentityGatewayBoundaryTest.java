package server.agents.integration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentIdentityGatewayBoundaryTest {
    @Test
    void identitySqlStaysAtCosmicBoundaryAndProvisioningRegistersCharacters() throws Exception {
        String command = Files.readString(Path.of(
                "src/main/java/server/agents/commands/AgentSpawnCommandExecutor.java"));
        String cosmic = Files.readString(Path.of(
                "src/main/java/server/agents/integration/cosmic/CosmicAgentIdentityGateway.java"));
        String roster = Files.readString(Path.of(
                "src/main/java/server/agents/integration/cosmic/CosmicAgentPersistenceGateway.java"));

        assertFalse(command.contains("tools.DatabaseConnection"));
        assertTrue(command.contains("AgentIdentityGatewayRuntime.identities().register"));
        assertTrue(cosmic.contains("tools.DatabaseConnection"));
        assertTrue(cosmic.contains("FROM agent_characters"));
        assertTrue(cosmic.contains("INSERT INTO agent_characters"));
        assertTrue(roster.contains("JOIN agent_characters"));
        assertFalse(roster.contains("a.banreason"));
    }
}
