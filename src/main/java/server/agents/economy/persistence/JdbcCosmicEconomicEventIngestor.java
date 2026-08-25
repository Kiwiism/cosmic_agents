package server.agents.economy.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.economy.domain.*;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Atomically promotes attributed Cosmic receipts into the canonical PostgreSQL ledger. */
public final class JdbcCosmicEconomicEventIngestor {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DataSource dataSource;

    public JdbcCosmicEconomicEventIngestor(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    public Result ingest(int limit) {
        return ingest(null, limit);
    }

    /**
     * Ingests evidence for one simulation run. Runtime callers must use this
     * scoped form so a quarantinable receipt from an older run cannot stop an
     * otherwise independent scenario.
     */
    public Result ingest(UUID runId, int limit) {
        if (limit <= 0) throw new IllegalArgumentException("limit must be positive");
        List<CosmicOutboxRecord> pending = pending(runId, limit);
        int ingested = 0;
        for (CosmicOutboxRecord receipt : pending) {
            try {
                if (ingestOne(receipt)) ingested++;
            } catch (RuntimeException failure) {
                quarantine(receipt, failure);
                return new Result(ingested, 1, receipt.outboxId());
            }
        }
        return new Result(ingested, 0, null);
    }

    private boolean ingestOne(CosmicOutboxRecord receipt) {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (alreadyIngested(connection, receipt)) {
                    connection.rollback();
                    return false;
                }
                CosmicOutboxEventTranslator translator = new CosmicOutboxEventTranslator(
                        (run, character, agent) -> participant(connection, run, character, agent),
                        (run, account, item, fingerprint, quantity) -> lots(
                                connection, run, account, item, fingerprint, quantity),
                        (run, activity) -> activity(connection, run, activity));
                CosmicOutboxEventTranslator.IngestionPlan plan = translator.translate(receipt);
                insertEvent(connection, plan.event());
                insertPostings(connection, plan.event());
                insertLots(connection, plan);
                updateInstances(connection, plan.event());
                materializeMarketLifecycle(connection, receipt, plan.event());
                insertTransaction(connection, receipt, plan.event());
                connection.commit();
                return true;
            } catch (SQLException | JsonProcessingException failure) {
                connection.rollback();
                throw new EconomyPersistenceException("Could not ingest Cosmic receipt", failure);
            } catch (RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not ingest Cosmic receipt", failure);
        }
    }

    private List<CosmicOutboxRecord> pending(UUID runId, int limit) {
        String runFilter = runId == null ? "" : "AND r.run_id = ? ";
        String sql = "SELECT r.* FROM cosmic_outbox_receipt r WHERE r.run_id IS NOT NULL " + runFilter
                + "AND NOT EXISTS (SELECT 1 FROM economic_event e WHERE e.run_id = r.run_id "
                + "AND e.idempotency_key = 'cosmic:' || r.outbox_id::text) "
                + "AND NOT EXISTS (SELECT 1 FROM economic_ingestion_failure f WHERE f.outbox_id = r.outbox_id) "
                + "ORDER BY r.logical_at, r.cosmic_created_at, r.outbox_id LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            if (runId != null) statement.setObject(parameter++, runId);
            statement.setInt(parameter, limit);
            List<CosmicOutboxRecord> result = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(receipt(rows));
            }
            return List.copyOf(result);
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not read pending Cosmic receipts", failure);
        }
    }

    private static CosmicOutboxRecord receipt(ResultSet row) throws SQLException {
        Number secondary = (Number) row.getObject("secondary_character_id");
        Timestamp logicalAt = row.getTimestamp("logical_at");
        return new CosmicOutboxRecord(row.getObject("outbox_id", UUID.class),
                row.getString("idempotency_key"), row.getString("operation_kind"),
                row.getInt("primary_character_id"), secondary == null ? null : secondary.intValue(),
                row.getString("summary"), row.getString("payload"), row.getObject("run_id", UUID.class),
                logicalAt == null ? null : logicalAt.toInstant(), row.getString("decision_id"),
                row.getString("activity_id"), row.getString("config_revision"),
                row.getString("catalog_revision"), row.getString("reason_code"),
                row.getBoolean("primary_is_agent"), row.getBoolean("secondary_is_agent"),
                row.getTimestamp("cosmic_created_at").toInstant());
    }

    private static boolean alreadyIngested(Connection connection, CosmicOutboxRecord receipt)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM economic_event WHERE run_id = ? AND idempotency_key = ?")) {
            statement.setObject(1, receipt.runId());
            statement.setString(2, "cosmic:" + receipt.outboxId());
            try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
        }
    }

    private static CosmicOutboxEventTranslator.Participant participant(
            Connection connection, UUID runId, int characterId, boolean markedAgent) {
        if (!markedAgent) {
            String id = "character:" + characterId;
            return new CosmicOutboxEventTranslator.Participant(id, new LedgerAccount("HUMAN", id));
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT agent_id FROM agent_character_binding WHERE run_id = ? AND character_id = ?")) {
            statement.setObject(1, runId); statement.setInt(2, characterId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new CosmicOutboxEventTranslator.EvidenceMismatchException(
                        "agent character has no run binding: " + characterId);
                String id = rows.getString(1);
                return new CosmicOutboxEventTranslator.Participant(id, LedgerAccount.agent(id));
            }
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not resolve economy participant", failure);
        }
    }

    private static List<CosmicOutboxEventTranslator.LotSlice> lots(
            Connection connection, UUID runId, LedgerAccount account, int itemId,
            String fingerprint, long requested) {
        String sql = "SELECT p.lot_id, SUM(p.quantity) AS available, MIN(e.logical_time) AS acquired_at "
                + "FROM ledger_posting p JOIN economic_event e ON e.event_id = p.event_id "
                + "JOIN item_lot l ON l.run_id = e.run_id AND l.lot_id = p.lot_id "
                + "WHERE e.run_id = ? AND p.account_type = ? AND p.account_owner_id = ? "
                + "AND p.asset_type = 'ITEM' AND p.asset_identifier = ? AND p.lot_id IS NOT NULL "
                + "AND (? = '' OR l.fingerprint = ? OR l.fingerprint IS NULL) "
                + "GROUP BY p.lot_id, l.fingerprint HAVING SUM(p.quantity) > 0 "
                + "ORDER BY CASE WHEN l.fingerprint = ? THEN 0 ELSE 1 END, acquired_at, p.lot_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, runId); statement.setString(2, account.type());
            statement.setString(3, account.ownerId()); statement.setString(4, Integer.toString(itemId));
            statement.setString(5, fingerprint); statement.setString(6, fingerprint);
            statement.setString(7, fingerprint);
            List<CosmicOutboxEventTranslator.LotSlice> result = new ArrayList<>();
            long remaining = requested;
            try (ResultSet rows = statement.executeQuery()) {
                while (remaining > 0 && rows.next()) {
                    long quantity = Math.min(remaining, rows.getLong("available"));
                    result.add(new CosmicOutboxEventTranslator.LotSlice(rows.getString("lot_id"), quantity));
                    remaining -= quantity;
                }
            }
            return List.copyOf(result);
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not allocate FIFO item lots", failure);
        }
    }

    private static CosmicOutboxEventTranslator.FarmEvidence activity(
            Connection connection, UUID runId, String activityId) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT outcome FROM activity_session WHERE run_id = ? AND activity_id = ? "
                        + "AND status = 'COMPLETED'")) {
            statement.setObject(1, runId); statement.setString(2, activityId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new CosmicOutboxEventTranslator.EvidenceMismatchException(
                        "completed activity evidence is missing: " + activityId);
                JsonNode value = JSON.readTree(rows.getString(1));
                List<CosmicOutboxEventTranslator.FarmDrop> drops = new ArrayList<>();
                for (JsonNode drop : value.path("itemDrops")) {
                    Map<String, Object> stats = JSON.convertValue(drop.path("equipmentStats"), Map.class);
                    drops.add(new CosmicOutboxEventTranslator.FarmDrop(drop.path("lotId").asText(),
                            drop.path("monsterId").asInt(), drop.path("itemId").asInt(),
                            drop.path("quantity").asInt(), stats));
                }
                List<CosmicOutboxEventTranslator.FarmConsumption> consumed = new ArrayList<>();
                for (JsonNode use : value.path("consumedItems"))
                    consumed.add(new CosmicOutboxEventTranslator.FarmConsumption(
                            use.path("itemId").asInt(), use.path("quantity").asInt()));
                return new CosmicOutboxEventTranslator.FarmEvidence(value.path("experience").asLong(),
                        value.path("mesos").asLong(), drops, consumed);
            }
        } catch (SQLException | JsonProcessingException failure) {
            throw new EconomyPersistenceException("Could not resolve completed farm evidence", failure);
        }
    }

    private static void insertEvent(Connection connection, EconomicEvent event)
            throws SQLException, JsonProcessingException {
        String sql = "INSERT INTO economic_event (event_id, run_id, logical_time, event_kind, "
                + "idempotency_key, causation_id, correlation_id, config_hash, catalog_version, actor_ids, evidence) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb))";
        try (PreparedStatement s = connection.prepareStatement(sql)) {
            s.setObject(1, event.eventId()); s.setObject(2, event.runId());
            s.setTimestamp(3, Timestamp.from(event.logicalTime())); s.setString(4, event.kind().name());
            s.setString(5, event.idempotencyKey()); s.setString(6, nullable(event.causationId()));
            s.setString(7, nullable(event.correlationId())); s.setString(8, event.configHash());
            s.setString(9, event.catalogVersion()); s.setString(10, JSON.writeValueAsString(event.actorIds()));
            s.setString(11, JSON.writeValueAsString(event.evidence())); s.executeUpdate();
        }
    }

    private static void insertPostings(Connection connection, EconomicEvent event) throws SQLException {
        String sql = "INSERT INTO ledger_posting (event_id, posting_index, account_type, account_owner_id, "
                + "asset_type, asset_identifier, quantity, lot_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement s = connection.prepareStatement(sql)) {
            int index = 0;
            for (LedgerPosting p : event.postings()) {
                s.setObject(1, event.eventId()); s.setInt(2, index++); s.setString(3, p.account().type());
                s.setString(4, p.account().ownerId()); s.setString(5, p.asset().type().name());
                s.setString(6, p.asset().identifier()); s.setLong(7, p.quantity());
                s.setString(8, nullable(p.lotId())); s.addBatch();
            }
            s.executeBatch();
        }
    }

    private static void insertLots(Connection connection, CosmicOutboxEventTranslator.IngestionPlan plan)
            throws SQLException, JsonProcessingException {
        String lotSql = "INSERT INTO item_lot (run_id, lot_id, item_id, created_event_id, source_kind, "
                + "source_identifier, original_quantity, attributes, fingerprint) VALUES (?, ?, ?, ?, ?, ?, ?, "
                + "CAST(? AS jsonb), ?)";
        String instanceSql = "INSERT INTO item_instance (run_id, instance_id, lot_id, item_id, equipment_stats, "
                + "current_owner_id, current_location) VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?)";
        try (PreparedStatement lot = connection.prepareStatement(lotSql);
             PreparedStatement instance = connection.prepareStatement(instanceSql)) {
            for (var value : plan.createdLots()) {
                lot.setObject(1, plan.event().runId()); lot.setString(2, value.lotId());
                lot.setInt(3, value.itemId()); lot.setObject(4, plan.event().eventId());
                lot.setString(5, value.sourceKind()); lot.setString(6, value.sourceIdentifier());
                lot.setLong(7, value.quantity()); lot.setString(8, JSON.writeValueAsString(value.attributes()));
                lot.setString(9, nullable(Objects.toString(value.attributes().get("fingerprint"), "")));
                lot.addBatch();
                for (var item : value.instances()) {
                    instance.setObject(1, plan.event().runId()); instance.setString(2, item.instanceId());
                    instance.setString(3, item.lotId()); instance.setInt(4, item.itemId());
                    instance.setString(5, JSON.writeValueAsString(item.stats()));
                    instance.setString(6, item.ownerId()); instance.setString(7, item.location());
                    instance.addBatch();
                }
            }
            lot.executeBatch(); instance.executeBatch();
        }
    }

    private static void updateInstances(Connection connection, EconomicEvent event) throws SQLException {
        String sql = "UPDATE item_instance SET current_owner_id = ?, current_location = ?, "
                + "destroyed_event_id = CASE WHEN ? = 'SINK' THEN ? ELSE destroyed_event_id END "
                + "WHERE run_id = ? AND lot_id = ?";
        try (PreparedStatement s = connection.prepareStatement(sql)) {
            for (LedgerPosting p : event.postings()) {
                if (p.quantity() <= 0 || p.asset().type() != AssetType.ITEM || p.lotId().isBlank()) continue;
                s.setString(1, p.account().ownerId()); s.setString(2, p.account().type());
                s.setString(3, p.account().type()); s.setObject(4, event.eventId());
                s.setObject(5, event.runId()); s.setString(6, p.lotId()); s.addBatch();
            }
            s.executeBatch();
        }
    }

    private static void insertTransaction(Connection connection, CosmicOutboxRecord receipt,
                                          EconomicEvent event) throws SQLException, JsonProcessingException {
        String summary = receipt.summary() == null ? "" : receipt.summary();
        Map<String, String> facts = new LinkedHashMap<>();
        for (String token : summary.split("\\s+")) {
            int at = token.indexOf('='); if (at > 0) facts.put(token.substring(0, at), token.substring(at + 1));
        }
        Integer itemId = integer(facts.get("item")); Integer quantity = integer(facts.get("quantity"));
        Long gross = longValue(facts.getOrDefault("gross", facts.get("mesos")));
        long tax = Optional.ofNullable(longValue(facts.get("buyerTax"))).orElse(0L)
                + Optional.ofNullable(longValue(facts.get("sellerTax"))).orElse(0L);
        String buyer = null, seller = null;
        if (receipt.operationKind().equals("PLAYER_SHOP_SALE")) {
            buyer = event.actorIds().get(0); seller = event.actorIds().get(1);
        } else if (receipt.operationKind().equals("PLAYER_TRADE")) {
            DirectTradeProjection projection = directTradeProjection(event);
            if (projection != null) {
                buyer = projection.buyer(); seller = projection.seller();
                itemId = projection.itemId(); quantity = projection.quantity(); gross = projection.grossMesos();
            }
        } else if (receipt.operationKind().equals("SHOP_BUY") || receipt.operationKind().equals("SHOP_RECHARGE")) {
            buyer = event.actorIds().getFirst();
        } else if (receipt.operationKind().equals("SHOP_SELL")) seller = event.actorIds().getFirst();
        String listingId = nestedText(event.evidence(), "marketSale", "listingId");
        String sql = "INSERT INTO economic_transaction (run_id, transaction_id, committed_event_id, "
                + "transaction_kind, buyer_id, seller_id, item_id, quantity, gross_mesos, tax_mesos, "
                + "human_counterparty, logical_at, evidence, listing_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)";
        try (PreparedStatement s = connection.prepareStatement(sql)) {
            s.setObject(1, event.runId()); s.setString(2, receipt.outboxId().toString());
            s.setObject(3, event.eventId()); s.setString(4, receipt.operationKind());
            s.setString(5, buyer); s.setString(6, seller); setInteger(s, 7, itemId); setInteger(s, 8, quantity);
            setLong(s, 9, gross); s.setLong(10, tax);
            s.setBoolean(11, !receipt.primaryIsAgent() || (receipt.secondaryCharacterId() != null && !receipt.secondaryIsAgent()));
            s.setTimestamp(12, Timestamp.from(event.logicalTime()));
            s.setString(13, JSON.writeValueAsString(event.evidence())); s.setString(14, listingId);
            s.executeUpdate();
        }
    }

    /** Prices only one-way, one-item-kind trades. Barter and mixed baskets intentionally remain unpriced. */
    private static DirectTradeProjection directTradeProjection(EconomicEvent event) {
        Map<String, Long> receivedItems = new LinkedHashMap<>();
        Map<String, Long> sentItems = new LinkedHashMap<>();
        Integer itemId = null;
        for (LedgerPosting posting : event.postings()) {
            if (posting.asset().type() != AssetType.ITEM
                    || !(posting.account().type().equals("AGENT") || posting.account().type().equals("HUMAN")))
                continue;
            int current = Integer.parseInt(posting.asset().identifier());
            if (itemId != null && itemId != current) return null;
            itemId = current;
            Map<String, Long> side = posting.quantity() > 0 ? receivedItems : sentItems;
            side.merge(posting.account().ownerId(), Math.abs(posting.quantity()), Math::addExact);
        }
        if (itemId == null || receivedItems.size() != 1 || sentItems.size() != 1) return null;
        String buyer = receivedItems.keySet().iterator().next();
        String seller = sentItems.keySet().iterator().next();
        long received = receivedItems.get(buyer);
        if (received <= 0 || received != sentItems.get(seller)) return null;
        long gross = 0;
        for (LedgerPosting posting : event.postings()) {
            if (posting.asset().type() == AssetType.MESO && posting.account().ownerId().equals(buyer)
                    && posting.quantity() < 0) gross = Math.addExact(gross, -posting.quantity());
        }
        if (gross <= 0) return null;
        return new DirectTradeProjection(buyer, seller, itemId, Math.toIntExact(received), gross);
    }

    private record DirectTradeProjection(String buyer, String seller, int itemId,
                                         int quantity, long grossMesos) { }

    @SuppressWarnings("unchecked")
    private static void materializeMarketLifecycle(Connection connection, CosmicOutboxRecord receipt,
                                                   EconomicEvent event) throws SQLException {
        Object opened = event.evidence().get("marketStall");
        if (opened instanceof Map<?, ?> raw) {
            Map<String, Object> stall = (Map<String, Object>) raw;
            insertStall(connection, event, stall);
            insertListings(connection, event, stall);
        }
        Object sale = event.evidence().get("marketSale");
        if (sale instanceof Map<?, ?> raw) updateListingSale(connection, event, (Map<String, Object>) raw);
        Object closed = event.evidence().get("marketStallClose");
        if (closed instanceof Map<?, ?> raw) closeStall(connection, event, (Map<String, Object>) raw);
    }

    private static void insertStall(Connection connection, EconomicEvent event, Map<String, Object> stall)
            throws SQLException {
        try (PreparedStatement s = connection.prepareStatement(
                "INSERT INTO market_stall (run_id, stall_id, seller_id, room_map_id, spot_x, opened_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            s.setObject(1, event.runId()); s.setString(2, text(stall, "stallId"));
            s.setString(3, event.actorIds().getFirst()); s.setInt(4, number(stall, "roomMapId").intValue());
            s.setInt(5, number(stall, "spotX").intValue()); s.setTimestamp(6, Timestamp.from(event.logicalTime()));
            s.executeUpdate();
        }
    }

    @SuppressWarnings("unchecked")
    private static void insertListings(Connection connection, EconomicEvent event, Map<String, Object> stall)
            throws SQLException {
        Map<Integer, Deque<LotQuantity>> available = new HashMap<>();
        for (LedgerPosting posting : event.postings()) {
            if (posting.quantity() > 0 && "ESCROW".equals(posting.account().type())
                    && posting.asset().type() == AssetType.ITEM) {
                int itemId = Integer.parseInt(posting.asset().identifier());
                available.computeIfAbsent(itemId, ignored -> new ArrayDeque<>())
                        .add(new LotQuantity(posting.lotId(), posting.quantity()));
            }
        }
        // The translator sends the consumed PlayerShop permit to a lifecycle sink.
        // Only actual merchandise reaches escrow and may be allocated to listings.
        String listingSql = "INSERT INTO market_listing (run_id, listing_id, stall_id, seller_id, "
                + "room_map_id, item_id, lot_id, quantity_per_bundle, bundles_initial, bundles_remaining, "
                + "bundle_price, opened_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String lotSql = "INSERT INTO market_listing_lot (run_id, listing_id, lot_id, quantity_initial) "
                + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement listing = connection.prepareStatement(listingSql);
             PreparedStatement allocation = connection.prepareStatement(lotSql)) {
            for (Map<String, Object> row : (List<Map<String, Object>>) stall.get("listings")) {
                int itemId = number(row, "itemId").intValue();
                long needed = Math.multiplyExact(number(row, "quantityPerBundle").longValue(),
                        number(row, "bundles").longValue());
                List<LotQuantity> lots = take(available.get(itemId), needed);
                String listingId = text(row, "listingId");
                listing.setObject(1, event.runId()); listing.setString(2, listingId);
                listing.setString(3, text(stall, "stallId")); listing.setString(4, event.actorIds().getFirst());
                listing.setInt(5, number(stall, "roomMapId").intValue()); listing.setInt(6, itemId);
                listing.setString(7, lots.getFirst().lotId());
                listing.setInt(8, number(row, "quantityPerBundle").intValue());
                listing.setInt(9, number(row, "bundles").intValue());
                listing.setInt(10, number(row, "bundles").intValue());
                listing.setLong(11, number(row, "bundlePrice").longValue());
                listing.setTimestamp(12, Timestamp.from(event.logicalTime())); listing.addBatch();
                for (LotQuantity lot : lots) {
                    allocation.setObject(1, event.runId()); allocation.setString(2, listingId);
                    allocation.setString(3, lot.lotId()); allocation.setLong(4, lot.quantity());
                    allocation.addBatch();
                }
            }
            listing.executeBatch(); allocation.executeBatch();
        }
        if (available.values().stream().flatMap(Collection::stream).anyMatch(value -> value.quantity() != 0))
            throw new CosmicOutboxEventTranslator.EvidenceMismatchException(
                    "stall listing evidence does not consume all escrow postings");
    }

    private static List<LotQuantity> take(Deque<LotQuantity> available, long requested) {
        if (available == null) throw new CosmicOutboxEventTranslator.EvidenceMismatchException(
                "stall listing has no matching escrow posting");
        List<LotQuantity> result = new ArrayList<>();
        long remaining = requested;
        while (remaining > 0 && !available.isEmpty()) {
            LotQuantity current = available.removeFirst();
            long quantity = Math.min(remaining, current.quantity());
            result.add(new LotQuantity(current.lotId(), quantity)); remaining -= quantity;
            if (current.quantity() > quantity)
                available.addFirst(new LotQuantity(current.lotId(), current.quantity() - quantity));
        }
        if (remaining != 0) throw new CosmicOutboxEventTranslator.EvidenceMismatchException(
                "stall listing quantity exceeds exact escrow postings");
        return result;
    }

    private static void updateListingSale(Connection connection, EconomicEvent event, Map<String, Object> sale)
            throws SQLException {
        int purchased = number(sale, "bundlesPurchased").intValue();
        int remaining = number(sale, "bundlesRemaining").intValue();
        try (PreparedStatement s = connection.prepareStatement(
                "UPDATE market_listing SET bundles_remaining = ?, closed_at = CASE WHEN ? = 0 THEN ? ELSE closed_at END, "
                        + "close_reason = CASE WHEN ? = 0 THEN 'SOLD_OUT' ELSE close_reason END "
                        + "WHERE run_id = ? AND listing_id = ? AND bundles_remaining = ?")) {
            s.setInt(1, remaining); s.setInt(2, remaining); s.setTimestamp(3, Timestamp.from(event.logicalTime()));
            s.setInt(4, remaining); s.setObject(5, event.runId()); s.setString(6, text(sale, "listingId"));
            s.setInt(7, Math.addExact(remaining, purchased));
            if (s.executeUpdate() != 1) throw new CosmicOutboxEventTranslator.EvidenceMismatchException(
                    "stall sale does not match listing state");
        }
        try (PreparedStatement s = connection.prepareStatement(
                "UPDATE market_stall SET closed_at = ?, close_reason = 'SOLD_OUT' WHERE run_id = ? AND stall_id = ? "
                        + "AND closed_at IS NULL AND NOT EXISTS (SELECT 1 FROM market_listing l WHERE l.run_id = ? "
                        + "AND l.stall_id = ? AND l.closed_at IS NULL)")) {
            s.setTimestamp(1, Timestamp.from(event.logicalTime())); s.setObject(2, event.runId());
            s.setString(3, text(sale, "stallId")); s.setObject(4, event.runId());
            s.setString(5, text(sale, "stallId")); s.executeUpdate();
        }
    }

    private static void closeStall(Connection connection, EconomicEvent event, Map<String, Object> close)
            throws SQLException {
        String reason = text(close, "reason");
        try (PreparedStatement s = connection.prepareStatement(
                "UPDATE market_listing SET closed_at = COALESCE(closed_at, ?), "
                        + "close_reason = COALESCE(close_reason, ?) WHERE run_id = ? AND stall_id = ?")) {
            s.setTimestamp(1, Timestamp.from(event.logicalTime())); s.setString(2, reason);
            s.setObject(3, event.runId()); s.setString(4, text(close, "stallId")); s.executeUpdate();
        }
        try (PreparedStatement s = connection.prepareStatement(
                "UPDATE market_stall SET closed_at = COALESCE(closed_at, ?), close_reason = COALESCE(close_reason, ?) "
                        + "WHERE run_id = ? AND stall_id = ?")) {
            s.setTimestamp(1, Timestamp.from(event.logicalTime())); s.setString(2, reason);
            s.setObject(3, event.runId()); s.setString(4, text(close, "stallId"));
            if (s.executeUpdate() != 1) throw new CosmicOutboxEventTranslator.EvidenceMismatchException(
                    "stall close has no materialized stall");
        }
    }

    @SuppressWarnings("unchecked")
    private static String nestedText(Map<String, Object> evidence, String parent, String key) {
        Object value = evidence.get(parent);
        return value instanceof Map<?, ?> ? Objects.toString(((Map<String, Object>) value).get(key), null) : null;
    }

    private static String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) throw new CosmicOutboxEventTranslator.EvidenceMismatchException("missing " + key);
        return value.toString();
    }
    private static Number number(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Number number)) throw new CosmicOutboxEventTranslator.EvidenceMismatchException(
                "missing numeric " + key);
        return number;
    }
    private record LotQuantity(String lotId, long quantity) { }

    private void quarantine(CosmicOutboxRecord receipt, RuntimeException failure) {
        String sql = "INSERT INTO economic_ingestion_failure (outbox_id, run_id, error_class, error_message, receipt) "
                + "VALUES (?, ?, ?, ?, CAST(? AS jsonb)) ON CONFLICT (outbox_id) DO UPDATE SET "
                + "failed_at = now(), attempts = economic_ingestion_failure.attempts + 1, "
                + "error_class = EXCLUDED.error_class, error_message = EXCLUDED.error_message, receipt = EXCLUDED.receipt";
        try (Connection connection = dataSource.getConnection(); PreparedStatement s = connection.prepareStatement(sql)) {
            s.setObject(1, receipt.outboxId()); s.setObject(2, receipt.runId());
            s.setString(3, failure.getClass().getName()); s.setString(4, truncate(failure.getMessage()));
            s.setString(5, quarantineReceiptJson(receipt)); s.executeUpdate();
        } catch (SQLException | JsonProcessingException persistenceFailure) {
            failure.addSuppressed(persistenceFailure);
            throw failure;
        }
    }

    private static Integer integer(String value) { return value == null ? null : Integer.valueOf(value); }
    private static Long longValue(String value) { return value == null ? null : Long.valueOf(value); }
    private static void setInteger(PreparedStatement s, int index, Integer value) throws SQLException {
        if (value == null) s.setNull(index, Types.INTEGER); else s.setInt(index, value);
    }
    private static void setLong(PreparedStatement s, int index, Long value) throws SQLException {
        if (value == null) s.setNull(index, Types.BIGINT); else s.setLong(index, value);
    }
    private static String nullable(String value) { return value == null || value.isBlank() ? null : value; }
    private static String truncate(String value) {
        String text = value == null ? "unknown ingestion failure" : value;
        return text.substring(0, Math.min(2048, text.length()));
    }

    static String quarantineReceiptJson(CosmicOutboxRecord receipt) throws JsonProcessingException {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("outboxId", receipt.outboxId());
        value.put("idempotencyKey", receipt.idempotencyKey());
        value.put("operationKind", receipt.operationKind());
        value.put("primaryCharacterId", receipt.primaryCharacterId());
        value.put("secondaryCharacterId", receipt.secondaryCharacterId());
        value.put("summary", receipt.summary());
        value.put("payloadJson", receipt.payloadJson());
        value.put("runId", receipt.runId());
        value.put("logicalAt", receipt.logicalAt() == null ? null : receipt.logicalAt().toString());
        value.put("decisionId", receipt.decisionId());
        value.put("activityId", receipt.activityId());
        value.put("configRevision", receipt.configRevision());
        value.put("catalogRevision", receipt.catalogRevision());
        value.put("reasonCode", receipt.reasonCode());
        value.put("primaryIsAgent", receipt.primaryIsAgent());
        value.put("secondaryIsAgent", receipt.secondaryIsAgent());
        value.put("createdAt", receipt.createdAt() == null ? null : receipt.createdAt().toString());
        return JSON.writeValueAsString(value);
    }

    public record Result(int ingested, int quarantined, UUID failedOutboxId) { }
}
