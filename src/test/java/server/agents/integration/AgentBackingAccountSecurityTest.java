package server.agents.integration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBackingAccountSecurityTest {
    @Test
    void provisioningDoesNotExposeOrStoreSharedCredential() throws Exception {
        String command = Files.readString(Path.of(
                "src/main/java/server/agents/commands/AgentSpawnCommandExecutor.java"));
        String persistence = Files.readString(Path.of(
                "src/main/java/server/agents/integration/cosmic/CosmicAgentPersistenceGateway.java"));

        assertFalse(command.contains("pw=botbot"));
        assertFalse(persistence.contains("hashpw(\"botbot\""));
        assertTrue(persistence.contains("SecureRandom"));
        assertFalse(persistence.contains("AgentAccountResolution.reused"));
    }
}
