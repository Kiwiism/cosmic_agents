package server.agents.integration;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBackingAccountLoginLockBoundaryTest {
    @Test
    void provisioningLocksAccountBeforeCreatingBackingCharacter() throws Exception {
        String spawn = Files.readString(Path.of(
                "src/main/java/server/agents/commands/AgentSpawnCommandExecutor.java"));

        int lock = spawn.indexOf("lockAgentBackingAccount(account.accountId())");
        int create = spawn.indexOf("createBackingCharacter(creationClient, botName)");
        assertTrue(lock >= 0);
        assertTrue(create > lock);
    }

    @Test
    void databaseLockLivesOnlyInCosmicAdapter() throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/server/agents/auth/AgentBackingAccountSecurityService.java"));
        String cosmic = Files.readString(Path.of(
                "src/main/java/server/agents/integration/cosmic/CosmicAgentBackingAccountSecurity.java"));

        assertFalse(service.contains("DatabaseConnection"));
        assertTrue(cosmic.contains("DatabaseConnection"));
        assertTrue(cosmic.contains("SET banned = 1"));
        assertTrue(cosmic.contains("Agent-only backing account"));
    }
}
