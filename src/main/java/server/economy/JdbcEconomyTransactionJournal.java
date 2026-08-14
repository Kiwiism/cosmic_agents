package server.economy;

import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;

public final class JdbcEconomyTransactionJournal implements EconomyTransactionJournal {
    @FunctionalInterface
    interface ConnectionProvider {
        Connection open() throws SQLException;
    }

    @FunctionalInterface
    interface CommitFailureInjector {
        void afterStatePersistence() throws SQLException;
    }

    private final ConnectionProvider connections;
    private final CommitFailureInjector failureInjector;

    public JdbcEconomyTransactionJournal() {
        this(DatabaseConnection::getConnection, () -> { });
    }

    JdbcEconomyTransactionJournal(ConnectionProvider connections) {
        this(connections, () -> { });
    }

    JdbcEconomyTransactionJournal(ConnectionProvider connections, CommitFailureInjector failureInjector) {
        this.connections = Objects.requireNonNull(connections);
        this.failureInjector = Objects.requireNonNull(failureInjector);
    }

    @Override
    public EconomyPrepareResult prepare(EconomyOperation operation) {
        Objects.requireNonNull(operation);
        String sql = "INSERT INTO economy_transaction_journal "
                + "(transaction_id, idempotency_key, operation_kind, status, primary_character_id, secondary_character_id, summary) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operation.transactionId().toString());
            statement.setString(2, operation.idempotencyKey());
            statement.setString(3, operation.kind().name());
            statement.setString(4, EconomyJournalStatus.PREPARED.name());
            statement.setInt(5, operation.primaryCharacterId());
            if (operation.secondaryCharacterId() == null) {
                statement.setNull(6, java.sql.Types.INTEGER);
            } else {
                statement.setInt(6, operation.secondaryCharacterId());
            }
            statement.setString(7, operation.summary());
            statement.executeUpdate();
            return EconomyPrepareResult.EXECUTE;
        } catch (SQLException e) {
            if (isConstraintViolation(e)) {
                return prepareExisting(operation);
            }
            throw new EconomyTransactionException("Could not prepare economy journal entry", e);
        }
    }

    @Override
    public void commit(EconomyOperation operation, EconomyDurableState durableState) {
        Objects.requireNonNull(operation);
        Objects.requireNonNull(durableState);
        try (Connection connection = connections.open()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                String status = lockStatus(connection, operation);
                if (EconomyJournalStatus.COMMITTED.name().equals(status)) {
                    connection.rollback();
                    return;
                }
                if (!EconomyJournalStatus.PREPARED.name().equals(status)) {
                    throw new EconomyTransactionException("Economy operation is not prepared");
                }
                durableState.persist(connection);
                failureInjector.afterStatePersistence();
                appendOutbox(connection, operation, durableState.evidenceJson());
                transition(connection, operation, EconomyJournalStatus.COMMITTED, null);
                connection.commit();
            } catch (SQLException failure) {
                rollback(connection, failure);
                throw failure;
            } catch (RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            if (isCommitted(operation)) {
                return;
            }
            throw new EconomyTransactionException("Could not atomically commit economy operation", e);
        }
    }

    @Override
    public void transition(EconomyOperation operation, EconomyJournalStatus status, String failureReason) {
        if (status == EconomyJournalStatus.COMMITTED) {
            throw new EconomyTransactionException("Committed state requires an atomic durable commit");
        }
        String sql = "UPDATE economy_transaction_journal SET status = ?, failure_reason = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE transaction_id = ? AND status = 'PREPARED'";
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setString(2, truncate(failureReason));
            statement.setString(3, operation.transactionId().toString());
            if (statement.executeUpdate() != 1) {
                throw new EconomyTransactionException("Economy journal transition was not unique");
            }
        } catch (SQLException e) {
            throw new EconomyTransactionException("Could not transition economy journal entry", e);
        }
    }

    @Override
    public int reconcileStalePrepared(Duration age) {
        long seconds = Math.max(1, age.toSeconds());
        String sql = "UPDATE economy_transaction_journal SET status = 'ROLLED_BACK', "
                + "failure_reason = 'Server restarted before the atomic durable commit', "
                + "updated_at = CURRENT_TIMESTAMP WHERE status = 'PREPARED' "
                + "AND updated_at < TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP)";
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, -seconds);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new EconomyTransactionException("Could not reconcile stale economy journal entries", e);
        }
    }

    private static String lockStatus(Connection connection, EconomyOperation operation) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT status FROM economy_transaction_journal WHERE transaction_id = ? FOR UPDATE")) {
            statement.setString(1, operation.transactionId().toString());
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new EconomyTransactionException("Economy journal entry is missing");
                }
                return result.getString(1);
            }
        }
    }

    private EconomyPrepareResult prepareExisting(EconomyOperation operation) {
        try (Connection connection = connections.open()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT operation_kind, status, primary_character_id, secondary_character_id "
                            + "FROM economy_transaction_journal WHERE idempotency_key = ? FOR UPDATE")) {
                statement.setString(1, operation.idempotencyKey());
                try (var result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new EconomyTransactionException("Idempotent economy operation disappeared");
                    }
                    Number secondaryValue = (Number) result.getObject("secondary_character_id");
                    Integer secondaryId = secondaryValue == null ? null : secondaryValue.intValue();
                    if (!operation.kind().name().equals(result.getString("operation_kind"))
                            || operation.primaryCharacterId() != result.getInt("primary_character_id")
                            || !Objects.equals(operation.secondaryCharacterId(), secondaryId)) {
                        throw new EconomyTransactionException("Idempotency key was reused for a different operation");
                    }
                    String status = result.getString("status");
                    if (EconomyJournalStatus.COMMITTED.name().equals(status)) {
                        connection.commit();
                        return EconomyPrepareResult.ALREADY_COMMITTED;
                    }
                    if (!EconomyJournalStatus.ROLLED_BACK.name().equals(status)) {
                        throw new EconomyTransactionException("Idempotent economy operation is already in progress");
                    }
                }
                rearmRolledBack(connection, operation);
                connection.commit();
                return EconomyPrepareResult.EXECUTE;
            } catch (SQLException | RuntimeException failure) {
                rollback(connection, failure);
                throw failure;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            throw new EconomyTransactionException("Could not resolve idempotent economy operation", e);
        }
    }

    private static void rearmRolledBack(Connection connection, EconomyOperation operation) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE economy_transaction_journal SET transaction_id = ?, status = 'PREPARED', summary = ?, "
                        + "failure_reason = NULL, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE idempotency_key = ? AND status = 'ROLLED_BACK'")) {
            statement.setString(1, operation.transactionId().toString());
            statement.setString(2, operation.summary());
            statement.setString(3, operation.idempotencyKey());
            if (statement.executeUpdate() != 1) {
                throw new EconomyTransactionException("Rolled-back economy operation could not be retried");
            }
        }
    }

    private static boolean isConstraintViolation(SQLException failure) {
        return failure.getSQLState() != null && failure.getSQLState().startsWith("23");
    }

    private static void transition(Connection connection, EconomyOperation operation,
                                   EconomyJournalStatus status, String failureReason) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE economy_transaction_journal SET status = ?, failure_reason = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE transaction_id = ? AND status = 'PREPARED'")) {
            statement.setString(1, status.name());
            statement.setString(2, truncate(failureReason));
            statement.setString(3, operation.transactionId().toString());
            if (statement.executeUpdate() != 1) {
                throw new EconomyTransactionException("Economy journal transition was not unique");
            }
        }
    }

    private static void appendOutbox(Connection connection, EconomyOperation operation,
                                     String payloadJson) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO economy_transaction_outbox "
                        + "(outbox_id, idempotency_key, operation_kind, primary_character_id, "
                        + "secondary_character_id, summary, payload_json) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, operation.transactionId().toString());
            statement.setString(2, operation.idempotencyKey());
            statement.setString(3, operation.kind().name());
            statement.setInt(4, operation.primaryCharacterId());
            if (operation.secondaryCharacterId() == null) {
                statement.setNull(5, java.sql.Types.INTEGER);
            } else {
                statement.setInt(5, operation.secondaryCharacterId());
            }
            statement.setString(6, operation.summary());
            statement.setString(7, payloadJson);
            statement.executeUpdate();
        }
    }

    private boolean isCommitted(EconomyOperation operation) {
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT status FROM economy_transaction_journal WHERE transaction_id = ?")) {
            statement.setString(1, operation.transactionId().toString());
            try (var result = statement.executeQuery()) {
                return result.next() && EconomyJournalStatus.COMMITTED.name().equals(result.getString(1));
            }
        } catch (SQLException ignored) {
            return false;
        }
    }

    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.substring(0, Math.min(value.length(), 1024));
    }
}
