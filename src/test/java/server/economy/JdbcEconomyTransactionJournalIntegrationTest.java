package server.economy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in MySQL gate for the production journal SQL. It never uses config.yaml and should point at
 * a disposable test schema through COSMIC_MYSQL_IT_URL, COSMIC_MYSQL_IT_USER, and
 * COSMIC_MYSQL_IT_PASSWORD. Set COSMIC_MYSQL_IT=true as an explicit mutation acknowledgement.
 */
class JdbcEconomyTransactionJournalIntegrationTest {
    private static String url;
    private static String user;
    private static String password;
    private final List<UUID> transactionIds = new ArrayList<>();

    @BeforeAll
    static void requireExplicitTestDatabase() throws Exception {
        assumeTrue(Boolean.parseBoolean(System.getenv("COSMIC_MYSQL_IT")),
                "real-MySQL integration test is opt-in");
        url = System.getenv("COSMIC_MYSQL_IT_URL");
        user = System.getenv("COSMIC_MYSQL_IT_USER");
        password = System.getenv("COSMIC_MYSQL_IT_PASSWORD");
        assumeTrue(url != null && !url.isBlank() && user != null && !user.isBlank(),
                "an isolated MySQL URL and user are required");
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS economy_transaction_journal (
                      transaction_id VARCHAR(36) NOT NULL PRIMARY KEY,
                      operation_kind VARCHAR(32) NOT NULL,
                      status VARCHAR(32) NOT NULL,
                      primary_character_id INT NOT NULL,
                      secondary_character_id INT NULL,
                      summary VARCHAR(1024) NOT NULL,
                      failure_reason VARCHAR(1024) NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP,
                      INDEX idx_economy_journal_status_updated (status, updated_at),
                      INDEX idx_economy_journal_primary_created (primary_character_id, created_at)
                    )
                    """);
        }
    }

    @AfterEach
    void removeTestRows() throws Exception {
        if (transactionIds.isEmpty()) {
            return;
        }
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM economy_transaction_journal WHERE transaction_id = ?")) {
            for (UUID transactionId : transactionIds) {
                statement.setString(1, transactionId.toString());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    @Test
    void concurrentOperationsCommitExactlyOnceAndRejectDuplicatePrepare() throws Exception {
        JdbcEconomyTransactionJournal journal = new JdbcEconomyTransactionJournal(
                JdbcEconomyTransactionJournalIntegrationTest::open);
        List<EconomyOperation> operations = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            EconomyOperation operation = EconomyOperation.create(
                    EconomyOperationKind.SHOP_BUY, 1_000_000 + i, null, "mysql-concurrency-gate");
            operations.add(operation);
            transactionIds.add(operation.transactionId());
        }

        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<?>> writes = new ArrayList<>();
            for (EconomyOperation operation : operations) {
                writes.add(executor.submit(() -> {
                    journal.prepare(operation);
                    journal.transition(operation, EconomyJournalStatus.COMMITTED, null);
                }));
            }
            for (Future<?> write : writes) {
                write.get();
            }
        }

        assertEquals(64, countStatus("COMMITTED"));
        assertThrows(EconomyTransactionException.class, () -> journal.prepare(operations.getFirst()));
        assertEquals(64, countStatus("COMMITTED"));
    }

    @Test
    void startupReconciliationMarksAnInterruptedPreparedOperationForReview() throws Exception {
        JdbcEconomyTransactionJournal journal = new JdbcEconomyTransactionJournal(
                JdbcEconomyTransactionJournalIntegrationTest::open);
        EconomyOperation interrupted = EconomyOperation.create(
                EconomyOperationKind.PLAYER_TRADE, 2_000_001, 2_000_002, "mysql-crash-gate");
        transactionIds.add(interrupted.transactionId());
        journal.prepare(interrupted);
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE economy_transaction_journal SET updated_at = CURRENT_TIMESTAMP - INTERVAL 10 MINUTE "
                             + "WHERE transaction_id = ?")) {
            statement.setString(1, interrupted.transactionId().toString());
            statement.executeUpdate();
        }

        assertEquals(1, journal.markStalePreparedForReview(Duration.ofMinutes(2)));
        assertEquals("REVIEW_REQUIRED", status(interrupted.transactionId()));
    }

    private static Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password == null ? "" : password);
    }

    private long countStatus(String status) throws Exception {
        String placeholders = String.join(",", transactionIds.stream().map(ignored -> "?").toList());
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM economy_transaction_journal WHERE status = ? "
                             + "AND transaction_id IN (" + placeholders + ")")) {
            statement.setString(1, status);
            for (int i = 0; i < transactionIds.size(); i++) {
                statement.setString(i + 2, transactionIds.get(i).toString());
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static String status(UUID transactionId) throws Exception {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT status FROM economy_transaction_journal WHERE transaction_id = ?")) {
            statement.setString(1, transactionId.toString());
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }
}
