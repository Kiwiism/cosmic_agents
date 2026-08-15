package server.agents.economy.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import server.agents.economy.catalog.CatalogBundleLoader;
import server.agents.economy.scenario.EconomyConfigLoader;
import server.agents.economy.catalog.EconomyCatalog;
import server.agents.economy.catalog.ItemFact;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
                    verifyOwnershipJournal(dataSource, connection, run);
                    verifySessionJournal(dataSource, connection, run);
                    verifyStallOfferStore(dataSource, connection, run);
                    verifyStructuredCommunication(dataSource, connection, run);
                    verifyAgentValuationKnowledge(dataSource, connection, run, loaded);
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
                    dashboardQuery(connection, "economy_session_trace.sql", Map.of(
                            ":run_id", runSql, ":agent_id", "'agent-1'"));
                    dashboardQuery(connection, "economic_intent_trace.sql", Map.of(
                            ":run_id", runSql, ":agent_id", "'agent-1'", ":item_id", "0"));
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

    private static void verifyOwnershipJournal(HikariDataSource dataSource, Connection connection,
                                               UUID run) throws Exception {
        var item = new server.agents.economy.ownership.InventoryItemRef(
                "ETC", (short) 1, 4000000, "a".repeat(64));
        var snapshot = new server.agents.economy.ownership.InventorySnapshot(101, "b".repeat(64),
                java.util.List.of(new server.agents.economy.ownership.InventoryItemSnapshot(
                        item, 2, Map.of("source", "integration"))));
        UUID authorizationId = UUID.randomUUID();
        var review = new server.agents.economy.ownership.InventoryReview(UUID.randomUUID(), run,
                "agent-1", snapshot, Instant.EPOCH,
                server.agents.economy.ownership.InventoryReview.Purpose.FM_MARKET_APPRAISAL,
                java.util.List.of(new server.agents.economy.ownership.InventoryDispositionDecision(
                        item, 1,
                        server.agents.economy.ownership.InventoryDispositionDecision.Disposition
                                .NPC_SALE_AUTHORIZED,
                        "integration", "SELL_TO_NPC", "SELL_TO_NPC", false)),
                java.util.List.of(new server.agents.economy.ownership.InventoryReview.AssetReservation(
                        UUID.randomUUID(), item, 1, "SELL_TO_NPC", "NPC_ANYWHERE")),
                java.util.List.of(new server.agents.economy.ownership.InventoryReview.ActionAuthorization(
                        authorizationId, item, 1, "SELL_TO_NPC", "NPC_ANYWHERE",
                        snapshot.revision(), Instant.EPOCH.plusSeconds(60))));
        var journal = new JdbcEconomyOwnershipJournal(dataSource);
        journal.appendReview(review);
        journal.markAuthorizationConsumed(authorizationId, Instant.EPOCH.plusSeconds(1));
        journal.appendGuardEvent(run, "agent-1", 101, Instant.EPOCH.plusSeconds(1),
                "SELL_TO_NPC", item, 1, true, "AUTHORIZED", authorizationId);

        assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM inventory_review WHERE run_id='"
                + run + "' AND inventory_revision='" + snapshot.revision() + "'"));
        assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM economic_action_authorization "
                + "WHERE authorization_id='" + authorizationId + "' AND status='CONSUMED'"));
        assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM economic_action_guard_event "
                + "WHERE authorization_id='" + authorizationId + "' AND allowed"));
        assertEquals(true, dashboardQuery(connection, "inventory_ownership_trace.sql", Map.of(
                ":run_id", "'" + run + "'::uuid", ":agent_id", "'agent-1'",
                ":item_id", "4000000")).contains("\"reviews\""));
    }

    private static void verifySessionJournal(HikariDataSource dataSource, Connection connection,
                                             UUID run) throws Exception {
        UUID request = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        var journal = new JdbcEconomyLifecycleJournal(dataSource);
        journal.sessionEvent(run, "agent-1", request, session, "ENTRY_ACCEPTED", Instant.EPOCH,
                "ACCEPTED", null, Instant.EPOCH.plusSeconds(1800));
        journal.sessionEvent(run, "agent-1", null, session, "RELEASE_RELEASED",
                Instant.EPOCH.plusSeconds(60), "MARKET_GOALS_COMPLETE", null, null);
        assertEquals(2, scalar(connection, "SELECT COUNT(*) FROM economy_session_event WHERE run_id='"
                + run + "' AND session_id='" + session + "'"));
    }

    private static void verifyStallOfferStore(HikariDataSource dataSource, Connection connection,
                                              UUID run) throws Exception {
        UUID lowerId = UUID.randomUUID();
        var lower = new server.agents.economy.market.StallOffer(lowerId, run,
                "agent-1", "agent-2", "stall-1", "stall-1:3", 910000001, 1302013,
                "exact-kfan", Map.of("watk", 50), 1, 400_000, 250_000,
                "untrusted public flavor", Instant.EPOCH.plusSeconds(10),
                Instant.EPOCH.plusSeconds(130),
                server.agents.economy.market.StallOffer.Status.PENDING);
        UUID winnerId = UUID.randomUUID();
        var winner = new server.agents.economy.market.StallOffer(winnerId, run,
                "agent-3", "agent-2", "stall-1", "stall-1:3", 910000001, 1302013,
                "exact-kfan", Map.of("watk", 50), 1, 400_000, 300_000,
                "higher public flavor", Instant.EPOCH.plusSeconds(20),
                Instant.EPOCH.plusSeconds(140),
                server.agents.economy.market.StallOffer.Status.PENDING);
        var store = new JdbcStallOfferStore(dataSource);
        store.create(lower); store.create(winner);
        assertEquals(300_000L, store.committedMesosForBuyer(run, "agent-3",
                Instant.EPOCH.plusSeconds(30)));
        assertEquals(winner, store.highestPendingForListing(run, "stall-1:3",
                Instant.EPOCH.plusSeconds(30)).orElseThrow());
        assertEquals(winner, store.pendingForSeller(run, "agent-2",
                Instant.EPOCH.plusSeconds(30), 10).getFirst());
        UUID arrangementId = UUID.randomUUID();
        var arrangement = new server.agents.economy.market.PrivateTradeArrangement(arrangementId,
                run, winnerId, "agent-3", "agent-2", "stall-1", "stall-1:3", 910000001,
                1302013, "exact-kfan", 1, 300_000, Instant.EPOCH.plusSeconds(40),
                Instant.EPOCH.plusSeconds(640),
                server.agents.economy.market.PrivateTradeArrangement.Status.PENDING_MEETUP);
        store.acceptForArrangement(winner, arrangement, "accepted", Instant.EPOCH.plusSeconds(40));
        assertEquals(300_000L, store.committedMesosForBuyer(run, "agent-3",
                Instant.EPOCH.plusSeconds(41)));
        store.resolve(winnerId, server.agents.economy.market.StallOffer.Status.EXECUTED,
                "settled", Instant.EPOCH.plusSeconds(40), "tx-structured-offer");
        assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM stall_offer WHERE offer_id='"
                + winnerId + "' AND status='EXECUTED' AND offered_mesos=300000 "
                + "AND settlement_transaction_id='tx-structured-offer'"));
        assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM stall_offer WHERE offer_id='"
                + lowerId + "' AND status='OUTBID'"));
        assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM private_trade_arrangement WHERE arrangement_id='"
                + arrangementId + "' AND status='PENDING_MEETUP' AND agreed_mesos=300000"));
        assertEquals(arrangement, store.pendingArrangementForBuyer(run, "agent-3",
                Instant.EPOCH.plusSeconds(41)).orElseThrow());
        store.resolveArrangement(arrangementId,
                server.agents.economy.market.PrivateTradeArrangement.Status.EXECUTED,
                Instant.EPOCH.plusSeconds(42), "tx-arrangement", "EXACT_TRADE_SETTLED");
        assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM private_trade_arrangement WHERE arrangement_id='"
                + arrangementId + "' AND status='EXECUTED' AND settlement_transaction_id='tx-arrangement' "
                + "AND resolution_reason='EXACT_TRADE_SETTLED'"));
    }

    private static void verifyStructuredCommunication(HikariDataSource dataSource, Connection connection,
                                                       UUID run) throws Exception {
        var communication = new JdbcEconomyCommunicationPort(run, dataSource);
        Instant at = Instant.EPOCH.plusSeconds(70);
        var publicInterest = communication.publish("agent-1", null,
                server.agents.economy.communication.EconomicIntent.Kind.BUY_INTEREST,
                1302013, "", 1, 0, null, "buying a clean Korean Fan",
                Map.of("build", "dexless"), at, java.time.Duration.ofMinutes(10));
        var directedOffer = communication.publish("agent-2", "agent-1",
                server.agents.economy.communication.EconomicIntent.Kind.MESO_OFFER,
                1302013, "exact-kfan", 1, 250_000, 910000001,
                "250k for your fan?", Map.of(), at.plusSeconds(1), java.time.Duration.ofMinutes(10));

        var visible = communication.discover("agent-1", 1302013, at.plusSeconds(2), 10);
        assertEquals(java.util.List.of(directedOffer), visible);
        assertEquals(1, communication.discover("agent-3", 1302013, at.plusSeconds(2), 10).size());
        assertEquals(true, communication.resolve("agent-1", directedOffer.intentId(),
                server.agents.economy.communication.EconomicIntent.Status.ACCEPTED,
                at.plusSeconds(3), "TERMS_ACCEPTED_PENDING_PHYSICAL_SETTLEMENT"));
        assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM economic_intent WHERE intent_id='"
                + directedOffer.intentId() + "' AND status='ACCEPTED' AND preferred_map_id=910000001"));
        assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM economic_intent WHERE intent_id='"
                + publicInterest.intentId() + "' AND preferred_map_id IS NULL "
                + "AND attributes->>'build'='dexless'"));
    }

    private static void verifyAgentValuationKnowledge(HikariDataSource dataSource, Connection connection,
                                                       UUID run,
                                                       server.agents.economy.scenario.LoadedEconomyConfig loaded) {
        EconomyCatalog catalog = mock(EconomyCatalog.class);
        when(catalog.item(1302013)).thenReturn(java.util.Optional.of(new ItemFact(1302013,
                "Korean Fan", 50_000, 35, 1, java.util.Set.of(), Map.of())));
        var journal = new JdbcEconomyEvidenceJournal(dataSource);
        journal.appendObservation(run, new server.agents.economy.market.MarketObservation(
                UUID.randomUUID().toString(), "agent-1", Instant.EPOCH.plusSeconds(50), 910000001,
                "agent-2", "stall-1:3", 1302013, 1, 300_000, 1, 1, 300_000,
                "exact-kfan", Map.of("watk", 50),
                server.agents.economy.market.MarketObservation.State.LISTED));
        var service = new JdbcAgentItemValuationService(run, dataSource, catalog,
                loaded.config().valuation);
        var observed = service.value("agent-1", 1302013, Instant.EPOCH.plusSeconds(60));
        assertEquals(server.agents.economy.market.AgentItemValuationService.Valuation.Source
                .PRIVATE_OBSERVATIONS, observed.source());
        assertEquals(300_000L, observed.unitValueMesos());

        var custom = new server.agents.economy.scenario.EconomyEngineConfig.ItemValueOverride();
        custom.itemId = 1302013; custom.unitValueMesos = 425_000; custom.reason = "balance audit";
        loaded.config().valuation.customOverrides = java.util.List.of(custom);
        var overridden = new JdbcAgentItemValuationService(run, dataSource, catalog,
                loaded.config().valuation).value("agent-1", 1302013, Instant.EPOCH.plusSeconds(61));
        assertEquals(server.agents.economy.market.AgentItemValuationService.Valuation.Source
                .CUSTOM_OVERRIDE, overridden.source());
        assertEquals(425_000L, overridden.unitValueMesos());
        assertEquals(2, scalarUnchecked(connection,
                "SELECT COUNT(*) FROM item_valuation_query WHERE run_id='" + run + "' AND item_id=1302013"));
    }

    private static int scalarUnchecked(Connection connection, String sql) {
        try { return scalar(connection, sql); }
        catch (SQLException failure) { throw new AssertionError(failure); }
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
        for (String table : java.util.List.of("economy_session_event", "economic_intent",
                "private_trade_arrangement", "stall_offer",
                "item_valuation_query", "market_observation")) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM " + table + " WHERE run_id = ?")) {
                statement.setObject(1, run); statement.executeUpdate();
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM economic_action_guard_event WHERE run_id = ?")) {
            statement.setObject(1, run); statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM inventory_review WHERE run_id = ?")) {
            statement.setObject(1, run); statement.executeUpdate();
        }
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
