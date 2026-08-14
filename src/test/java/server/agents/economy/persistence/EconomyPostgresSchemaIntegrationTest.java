package server.agents.economy.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import server.agents.economy.catalog.CatalogBundleLoader;
import server.agents.economy.scenario.EconomyConfigLoader;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfEnvironmentVariable(named = "ECONOMY_DB_INTEGRATION", matches = "true")
class EconomyPostgresSchemaIntegrationTest {
    @TempDir Path temporaryDirectory;

    @Test
    void cleanSchemaSatisfiesRuntimeContractAndRejectsUnbalancedEvents() throws Exception {
        try (HikariDataSource dataSource = EconomyPostgresDataSource.fromEnvironment()) {
            new EconomyDatabaseVerifier(dataSource).verify("cosmic_economy");
            try (Connection connection = dataSource.getConnection()) {
                assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM pg_trigger "
                        + "WHERE tgname = 'ledger_postings_must_balance'"));
                assertUnbalancedCommitFails(connection);
            }
            UUID run = UUID.randomUUID();
            try {
                try (Connection connection = dataSource.getConnection()) {
                    var loaded = new EconomyConfigLoader().load();
                    var catalog = new CatalogBundleLoader().load(loaded.config().catalog);
                    new JdbcSimulationRunRepository(dataSource).create(run, loaded, catalog);
                    assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM economy_config_revision "
                            + "WHERE run_id = '" + run + "' AND revision = 0 "
                            + "AND config_schema_version = 1 AND validation_result ->> 'valid' = 'true' "
                            + "AND normalized_config ->> 'schemaVersion' = '1'"));
                    var runs = new JdbcSimulationRunRepository(dataSource);
                    assertEquals("CREATED", runs.find(run).orElseThrow().status());
                    var checkpoint = new server.agents.economy.scenario.SimulationRunEngine.RunCheckpoint(
                            run, Instant.parse("2026-01-01T00:00:00Z"), loaded.sha256(),
                            catalog.version(), java.util.List.of(), Map.of("stream", 12L),
                            Map.of("z", Map.of("second", 2, "first", 1), "a", java.util.List.of(3, 4)));
                    runs.saveCheckpoint(checkpoint);
                    assertEquals(checkpoint, runs.latestCheckpoint(run).orElseThrow());
                    var bindings = new JdbcEconomyParticipantBindingStore(dataSource);
                    bindings.reserve(run, java.util.List.of(
                            new EconomyParticipantBindingStore.Reservation("agent-1", 101,
                                    Instant.parse("2026-01-01T00:00:00Z")),
                            new EconomyParticipantBindingStore.Reservation("agent-2", 102,
                                    Instant.parse("2026-01-02T00:00:00Z"))));
                    assertEquals(Map.of("agent-1", 101, "agent-2", 102), bindings.load(run));
                    runs.updateLogicalTime(run, Instant.parse("2026-01-01T00:00:01Z"),
                            "WAITING_PHYSICAL_ACTION");
                    runs.updateLogicalTime(run, Instant.parse("2026-01-01T00:00:02Z"),
                            "INVARIANT_VIOLATION");
                    assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM simulation_run WHERE run_id = '"
                            + run + "' AND status = 'INVARIANT_VIOLATION'"));
                    verifyExperimentPlanning(dataSource, connection);
                    insertProjectionFacts(connection, run);
                }
                JdbcEconomyProjectionService.Result projection =
                        new JdbcEconomyProjectionService(dataSource).rebuild(run);
                assertEquals(2, projection.itemDailyRows());
                assertEquals(new JdbcEconomyInvariantAuditor.Audit(true, java.util.List.of()),
                        new JdbcEconomyInvariantAuditor(dataSource).audit(run, Instant.EPOCH));
                try (Connection connection = dataSource.getConnection()) {
                    assertEquals(1, itemFlow(connection, run, 1102053, "quest_created_quantity"));
                    assertEquals(1, itemFlow(connection, run, 1102053, "transformed_created_quantity"));
                    assertEquals(0, itemFlow(connection, run, 1102053, "consumed_quantity"));
                    assertEquals(1, itemFlow(connection, run, 2041000, "consumed_quantity"));
                    String runSql = "'" + run + "'::uuid";
                    assertEquals(true, dashboardQuery(connection, "item_detail_dashboard.sql", Map.of(
                            ":run_id", runSql, ":item_id", "1102053")).contains("\"itemId\": 1102053"));
                    assertEquals(true, dashboardQuery(connection, "macro_dashboard.sql", Map.of(
                            ":run_id", runSql,
                            ":from_logical_at", "'1970-01-01T00:00:00Z'::timestamptz",
                            ":to_logical_at", "'2030-01-01T00:00:00Z'::timestamptz"))
                            .contains("\"moneySupply\""));
                    assertEquals(true, dashboardQuery(connection, "fixed_basket_price_index.sql", Map.of(
                            ":run_id", runSql,
                            ":base_logical_date", "'1970-01-01'::date",
                            ":from_logical_date", "'1970-01-01'::date",
                            ":to_logical_date", "'1970-01-02'::date",
                            ":basket_json", "'[{\"item_id\":1102053,\"quantity\":1}]'"))
                            .contains("1970-01-01"));
                    assertEquals(true, dashboardQuery(connection, "scenario_comparison.sql", Map.of(
                            ":baseline_run_id", runSql, ":candidate_run_id", runSql,
                            ":from_logical_at", "'1970-01-01T00:00:00Z'::timestamptz",
                            ":to_logical_at", "'2030-01-01T00:00:00Z'::timestamptz"))
                            .contains("MEASURED_DIFFERENCE_REQUIRES_PAIRED_DESIGN_FOR_CAUSAL_CLAIM"));
                    Map<String, String> runAndItem = Map.of(":run_id", runSql, ":item_id", "1102053");
                    dashboardQuery(connection, "item_history.sql", runAndItem);
                    dashboardQuery(connection, "item_market_explanation.sql", runAndItem);
                    dashboardQuery(connection, "invariant_audit.sql", Map.of(":run_id", runSql));
                    dashboardQuery(connection, "meso_flow.sql", Map.of(":run_id", runSql));
                    Map<String, String> trace = Map.of(":run_id", runSql,
                            ":agent_id", "'agent-1'",
                            ":from_logical_at", "'1970-01-01T00:00:00Z'::timestamptz",
                            ":to_logical_at", "'2030-01-01T00:00:00Z'::timestamptz");
                    dashboardQuery(connection, "agent_journal.sql", trace);
                    dashboardQuery(connection, "decision_trace.sql", trace);
                    dashboardQuery(connection, "economy_overview.sql", trace);
                }
            } finally {
                try (Connection connection = dataSource.getConnection()) { deleteRun(connection, run); }
            }
        }
    }

    private void verifyExperimentPlanning(HikariDataSource dataSource, Connection connection) throws Exception {
        Path baseline = temporaryDirectory.resolve("baseline.yaml");
        Path candidate = temporaryDirectory.resolve("candidate.yaml");
        Files.copy(Path.of("economy-engine.yaml"), baseline);
        Files.copy(Path.of("economy-engine.yaml"), candidate);
        String experimentId = "schema-audit-" + UUID.randomUUID();
        Path manifest = temporaryDirectory.resolve("experiment.yaml");
        Files.writeString(manifest, "schemaVersion: 1\n"
                + "experimentId: " + experimentId + "\n"
                + "description: PostgreSQL contract audit\n"
                + "design: PAIRED_SAME_SEED\n"
                + "pairs:\n"
                + "  - pairId: seed-1\n"
                + "    seed: 4815162342\n"
                + "    baselineConfig: baseline.yaml\n"
                + "    candidateConfig: candidate.yaml\n");
        var planner = new server.agents.economy.experiment.EconomyExperimentPlanner(dataSource);
        var plan = planner.plan(manifest);
        assertEquals(1, plan.pairs().size());
        assertEquals("BASELINE", planner.next(experimentId).side());
        assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM economy_experiment_pair WHERE experiment_id = '"
                + experimentId + "' AND baseline_config_hash = candidate_config_hash"));
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM economy_experiment WHERE experiment_id = ?")) {
            statement.setString(1, experimentId); statement.executeUpdate();
        }
    }

    private static void assertUnbalancedCommitFails(Connection connection) throws Exception {
        UUID run = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        connection.setAutoCommit(false);
        try {
            insertRun(connection, run);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO economic_event (event_id, run_id, logical_time, event_kind, "
                            + "idempotency_key, config_hash, catalog_version) VALUES (?, ?, ?, 'AUDIT', ?, ?, 'audit')")) {
                statement.setObject(1, event); statement.setObject(2, run);
                statement.setTimestamp(3, Timestamp.from(Instant.EPOCH));
                statement.setString(4, event.toString()); statement.setString(5, "0".repeat(64));
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO ledger_posting (event_id, posting_index, account_type, "
                            + "account_owner_id, asset_type, asset_identifier, quantity) "
                            + "VALUES (?, 0, 'SOURCE', 'audit', 'MESO', 'MESO', -1)")) {
                statement.setObject(1, event); statement.executeUpdate();
            }
            assertThrows(SQLException.class, connection::commit);
        } finally {
            connection.rollback();
            connection.setAutoCommit(true);
        }
    }

    private static void insertRun(Connection connection, UUID run) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO simulation_run (run_id, scenario_id, status, logical_started_at, "
                        + "logical_current_at, target_logical_at, seed, config_hash, config_yaml, "
                        + "catalog_version) VALUES (?, 'schema-audit', 'RUNNING', ?, ?, ?, 1, ?, '', 'audit')")) {
            statement.setObject(1, run);
            Timestamp at = Timestamp.from(Instant.EPOCH);
            statement.setTimestamp(2, at); statement.setTimestamp(3, at); statement.setTimestamp(4, at);
            statement.setString(5, "0".repeat(64)); statement.executeUpdate();
        }
    }

    private static void insertProjectionFacts(Connection connection, UUID run) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            UUID endowment = insertEvent(connection, run, "INITIAL_ENDOWMENT", "{}");
            posting(connection, endowment, 0, "SOURCE", "INITIAL_ENDOWMENT", 1102053, -1);
            posting(connection, endowment, 1, "AGENT", "agent-1", 1102053, 1);
            posting(connection, endowment, 2, "SOURCE", "INITIAL_ENDOWMENT", 2041000, -1);
            posting(connection, endowment, 3, "AGENT", "agent-1", 2041000, 1);
            UUID scroll = insertEvent(connection, run, "SCROLL_APPLIED",
                    "{\"scrollApplication\":{\"outcome\":\"SUCCESS\"}}");
            posting(connection, scroll, 0, "AGENT", "agent-1", 1102053, -1);
            posting(connection, scroll, 1, "SINK", "SCROLL_INPUT", 1102053, 1);
            posting(connection, scroll, 2, "SOURCE", "SCROLL_TRANSFORMATION:2041000", 1102053, -1);
            posting(connection, scroll, 3, "AGENT", "agent-1", 1102053, 1);
            posting(connection, scroll, 4, "AGENT", "agent-1", 2041000, -1);
            posting(connection, scroll, 5, "SINK", "SCROLL_CONSUMPTION", 2041000, 1);
            UUID quest = insertEvent(connection, run, "QUEST_TURN_IN", "{}");
            posting(connection, quest, 0, "SOURCE", "QUEST:2024:TURN_IN", 1102053, -1);
            posting(connection, quest, 1, "AGENT", "agent-1", 1102053, 1);
            connection.commit();
        } catch (SQLException failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static UUID insertEvent(Connection connection, UUID run, String kind, String evidence)
            throws SQLException {
        UUID event = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO economic_event (event_id, run_id, logical_time, event_kind, idempotency_key, "
                        + "config_hash, catalog_version, evidence) VALUES (?, ?, ?, ?, ?, ?, 'audit', CAST(? AS jsonb))")) {
            statement.setObject(1, event); statement.setObject(2, run);
            statement.setTimestamp(3, Timestamp.from(Instant.EPOCH)); statement.setString(4, kind);
            statement.setString(5, event.toString()); statement.setString(6, "0".repeat(64));
            statement.setString(7, evidence); statement.executeUpdate();
        }
        return event;
    }

    private static void posting(Connection connection, UUID event, int index, String accountType,
                                String owner, int itemId, long quantity) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ledger_posting (event_id, posting_index, account_type, account_owner_id, "
                        + "asset_type, asset_identifier, quantity) VALUES (?, ?, ?, ?, 'ITEM', ?, ?)")) {
            statement.setObject(1, event); statement.setInt(2, index); statement.setString(3, accountType);
            statement.setString(4, owner); statement.setString(5, Integer.toString(itemId));
            statement.setLong(6, quantity); statement.executeUpdate();
        }
    }

    private static int itemFlow(Connection connection, UUID run, int itemId, String column)
            throws SQLException {
        if (!java.util.Set.of("quest_created_quantity", "transformed_created_quantity", "consumed_quantity")
                .contains(column)) throw new IllegalArgumentException("unsupported audit column");
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + column + " FROM item_market_daily WHERE run_id = ? AND item_id = ?")) {
            statement.setObject(1, run); statement.setInt(2, itemId);
            try (var rows = statement.executeQuery()) { rows.next(); return rows.getInt(1); }
        }
    }

    private static void deleteRun(Connection connection, UUID run) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM agent_character_binding WHERE run_id = ?")) {
            statement.setObject(1, run); statement.executeUpdate();
        }
        for (String table : java.util.List.of("item_market_daily", "meso_flow_daily",
                "agent_state_projection", "listing_exposure", "simulation_checkpoint",
                "ledger_posting", "economic_event")) {
            String predicate = "ledger_posting".equals(table)
                    ? "event_id IN (SELECT event_id FROM economic_event WHERE run_id = ?)" : "run_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + table + " WHERE " + predicate)) {
                statement.setObject(1, run); statement.executeUpdate();
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM economy_invariant_violation WHERE run_id = ?")) {
            statement.setObject(1, run); statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM economy_config_revision WHERE run_id = ?")) {
            statement.setObject(1, run); statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM simulation_run WHERE run_id = ?")) {
            statement.setObject(1, run); statement.executeUpdate();
        }
    }

    private static int scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static String dashboardQuery(Connection connection, String file,
                                         Map<String, String> replacements) throws Exception {
        String sql = Files.readString(Path.of("economy-database", "queries", file));
        for (Map.Entry<String, String> replacement : replacements.entrySet())
            sql = sql.replace(replacement.getKey(), replacement.getValue());
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : "";
        }
    }
}
