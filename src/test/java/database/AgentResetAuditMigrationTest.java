package database;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentResetAuditMigrationTest {
    @Test
    void addsDurableResetAuditWithoutBlockingFutureCharacterDeletion() throws Exception {
        String changelog = Files.readString(Path.of("src/main/resources/db/changelog-tables.xml"));
        int start = changelog.indexOf("39-agent-clean-slate-reset-audit");
        int end = changelog.indexOf("</changeSet>", start);
        String changeSet = changelog.substring(start, end);

        assertTrue(changeSet.contains("<createTable tableName=\"agent_reset_audit\">"));
        assertTrue(changeSet.contains("confirmation_hash"));
        assertTrue(changeSet.contains("preview_json"));
        assertTrue(changeSet.contains("executed_at"));
        assertFalse(changeSet.contains("addForeignKeyConstraint"));
    }
}
