package database;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentIdentityMigrationTest {
    @Test
    void createsDurableIdentityAndBackfillsLegacyAgentAccounts() throws Exception {
        String changelog = Files.readString(Path.of("src/main/resources/db/changelog-tables.xml"));

        assertTrue(changelog.contains("37-agent-character-identity"));
        assertTrue(changelog.contains("<createTable tableName=\"agent_characters\">"));
        assertTrue(changelog.contains("fk_agent_character_identity"));
        assertTrue(changelog.contains("38-backfill-agent-character-identity"));
        assertTrue(changelog.contains("'LEGACY_BACKFILL'"));
        assertTrue(changelog.contains("a.banreason = 'Agent-only backing account'"));
        assertTrue(changelog.contains("NOT EXISTS ("));
    }
}
