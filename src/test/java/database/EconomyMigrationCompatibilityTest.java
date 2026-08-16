package database;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyMigrationCompatibilityTest {
    @Test
    void longTextPayloadUsesBackfillInsteadOfUnsupportedMySqlDefault() throws Exception {
        String changelog = Files.readString(Path.of("src/main/resources/db/changelog-tables.xml"));
        int start = changelog.indexOf("35-economy-outbox-machine-evidence");
        int end = changelog.indexOf("</changeSet>", start);
        String changeSet = changelog.substring(start, end);

        assertFalse(changeSet.contains("type=\"LONGTEXT\" defaultValue="));
        assertTrue(changeSet.contains("<update tableName=\"economy_transaction_outbox\">"));
        assertTrue(changeSet.contains("<addNotNullConstraint tableName=\"economy_transaction_outbox\""));
    }
}
