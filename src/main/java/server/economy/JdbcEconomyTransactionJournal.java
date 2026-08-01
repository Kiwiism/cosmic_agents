package server.economy;

import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;

public final class JdbcEconomyTransactionJournal implements EconomyTransactionJournal {
    @Override
    public void prepare(EconomyOperation operation) {
        Objects.requireNonNull(operation);
        String sql = "INSERT INTO economy_transaction_journal "
                + "(transaction_id, operation_kind, status, primary_character_id, secondary_character_id, summary) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, operation.transactionId().toString());
            statement.setString(2, operation.kind().name());
            statement.setString(3, EconomyJournalStatus.PREPARED.name());
            statement.setInt(4, operation.primaryCharacterId());
            if (operation.secondaryCharacterId() == null) {
                statement.setNull(5, java.sql.Types.INTEGER);
            } else {
                statement.setInt(5, operation.secondaryCharacterId());
            }
            statement.setString(6, operation.summary());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new EconomyTransactionException("Could not prepare economy journal entry", e);
        }
    }

    @Override
    public void transition(EconomyOperation operation, EconomyJournalStatus status, String failureReason) {
        String sql = "UPDATE economy_transaction_journal SET status = ?, failure_reason = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE transaction_id = ? AND status = 'PREPARED'";
        try (Connection connection = DatabaseConnection.getConnection();
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
    public int markStalePreparedForReview(Duration age) {
        long seconds = Math.max(1, age.toSeconds());
        String sql = "UPDATE economy_transaction_journal SET status = 'REVIEW_REQUIRED', "
                + "failure_reason = 'Server restarted before transaction outcome was durably recorded', "
                + "updated_at = CURRENT_TIMESTAMP WHERE status = 'PREPARED' "
                + "AND updated_at < TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP)";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, -seconds);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new EconomyTransactionException("Could not reconcile stale economy journal entries", e);
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.substring(0, Math.min(value.length(), 1024));
    }
}
