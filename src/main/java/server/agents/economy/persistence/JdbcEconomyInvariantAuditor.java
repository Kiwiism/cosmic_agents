package server.agents.economy.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Audits authoritative evidence; violations are durable data, never log-only warnings. */
public final class JdbcEconomyInvariantAuditor {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DataSource dataSource;

    public JdbcEconomyInvariantAuditor(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    public Audit audit(UUID runId, Instant logicalAt) {
        List<Check> checks = List.of(
                new Check("UNBALANCED_EVENT", "CRITICAL", UNBALANCED, 1),
                new Check("NEGATIVE_HOLDING", "CRITICAL", NEGATIVE_HOLDING, 1),
                new Check("LISTING_LOT_MISMATCH", "CRITICAL", LISTING_LOTS, 1),
                new Check("OPEN_LISTING_ESCROW_MISMATCH", "CRITICAL", OPEN_ESCROW, 2),
                new Check("ORPHAN_TRANSACTION_LISTING", "CRITICAL", ORPHAN_LISTING, 1),
                new Check("QUARANTINED_COSMIC_RECEIPT", "CRITICAL", QUARANTINED, 1));
        List<Violation> violations = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit(); connection.setAutoCommit(false);
            try {
                for (Check check : checks) {
                    long count = count(connection, check.sql(), runId, check.parameters());
                    if (count > 0) {
                        Violation value = new Violation(check.code(), check.severity(), count);
                        violations.add(value); insert(connection, runId, logicalAt, value);
                    }
                }
                connection.commit();
            } catch (SQLException | JsonProcessingException | RuntimeException failure) {
                connection.rollback(); throw failure;
            } finally { connection.setAutoCommit(autoCommit); }
        } catch (SQLException | JsonProcessingException failure) {
            throw new EconomyPersistenceException("Could not audit economy invariants", failure);
        }
        return new Audit(violations.isEmpty(), List.copyOf(violations));
    }

    private static long count(Connection connection, String sql, UUID runId, int parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 1; index <= parameters; index++) statement.setObject(index, runId);
            try (ResultSet row = statement.executeQuery()) { row.next(); return row.getLong(1); }
        }
    }

    private static void insert(Connection connection, UUID runId, Instant logicalAt, Violation violation)
            throws SQLException, JsonProcessingException {
        UUID id = UUID.nameUUIDFromBytes((runId + ":" + logicalAt + ':' + violation.code())
                .getBytes(StandardCharsets.UTF_8));
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO economy_invariant_violation (violation_id, run_id, logical_at, invariant_code, "
                        + "severity, evidence) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb)) ON CONFLICT DO NOTHING")) {
            statement.setObject(1, id); statement.setObject(2, runId);
            statement.setTimestamp(3, Timestamp.from(logicalAt)); statement.setString(4, violation.code());
            statement.setString(5, violation.severity());
            statement.setString(6, JSON.writeValueAsString(Map.of("affectedRows", violation.affectedRows())));
            statement.executeUpdate();
        }
    }

    private static final String UNBALANCED = """
            SELECT COUNT(*) FROM (SELECT e.event_id, p.asset_type, p.asset_identifier
                FROM economic_event e JOIN ledger_posting p USING (event_id)
                WHERE e.run_id = ? GROUP BY e.event_id, p.asset_type, p.asset_identifier
                HAVING SUM(p.quantity) <> 0) broken
            """;
    private static final String NEGATIVE_HOLDING = """
            SELECT COUNT(*) FROM (SELECT p.account_type, p.account_owner_id, p.asset_type,
                p.asset_identifier, p.lot_id FROM economic_event e JOIN ledger_posting p USING (event_id)
                WHERE e.run_id = ? AND p.account_type IN ('AGENT','HUMAN','ESCROW')
                GROUP BY p.account_type, p.account_owner_id, p.asset_type, p.asset_identifier, p.lot_id
                HAVING SUM(p.quantity) < 0) broken
            """;
    private static final String LISTING_LOTS = """
            SELECT COUNT(*) FROM (SELECT l.listing_id FROM market_listing l
                LEFT JOIN market_listing_lot a ON a.run_id = l.run_id AND a.listing_id = l.listing_id
                WHERE l.run_id = ? GROUP BY l.listing_id, l.quantity_per_bundle, l.bundles_initial
                HAVING COALESCE(SUM(a.quantity_initial), 0) <> l.quantity_per_bundle::bigint * l.bundles_initial) broken
            """;
    private static final String OPEN_ESCROW = """
            WITH expected AS (SELECT l.stall_id, l.item_id,
                    SUM(l.quantity_per_bundle::bigint * l.bundles_remaining) quantity
                FROM market_listing l WHERE l.run_id = ? AND l.closed_at IS NULL GROUP BY l.stall_id, l.item_id),
            actual AS (SELECT p.account_owner_id stall_id, p.asset_identifier::integer item_id, SUM(p.quantity) quantity
                FROM ledger_posting p JOIN economic_event e USING (event_id)
                WHERE e.run_id = ? AND p.account_type = 'ESCROW' AND p.asset_type = 'ITEM'
                GROUP BY p.account_owner_id, p.asset_identifier::integer)
            SELECT COUNT(*) FROM (SELECT COALESCE(e.stall_id, a.stall_id), COALESCE(e.item_id, a.item_id)
                FROM expected e FULL JOIN actual a USING (stall_id, item_id)
                WHERE COALESCE(e.quantity, 0) <> COALESCE(a.quantity, 0)) broken
            """;
    private static final String ORPHAN_LISTING = """
            SELECT COUNT(*) FROM economic_transaction t LEFT JOIN market_listing l
                ON l.run_id = t.run_id AND l.listing_id = t.listing_id
                WHERE t.run_id = ? AND t.listing_id IS NOT NULL AND l.listing_id IS NULL
            """;
    private static final String QUARANTINED =
            "SELECT COUNT(*) FROM economic_ingestion_failure WHERE run_id = ?";

    private record Check(String code, String severity, String sql, int parameters) { }
    public record Violation(String code, String severity, long affectedRows) { }
    public record Audit(boolean clean, List<Violation> violations) {
        public Audit { violations = List.copyOf(violations); }
    }
}
