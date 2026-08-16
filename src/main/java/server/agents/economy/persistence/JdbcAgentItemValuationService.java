package server.agents.economy.persistence;

import server.agents.economy.catalog.EconomyCatalog;
import server.agents.economy.market.AgentItemValuationService;
import server.agents.economy.scenario.EconomyEngineConfig;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Values items from only the requesting agent's durable observations, then catalog/override anchors. */
public final class JdbcAgentItemValuationService implements AgentItemValuationService {
    private final UUID runId;
    private final DataSource dataSource;
    private final EconomyCatalog catalog;
    private final Duration memory;
    private final int minimumObservations;
    private final int catalogMarkupBasisPoints;
    private final Map<Integer, ValueOverride> overrides;

    public JdbcAgentItemValuationService(UUID runId, DataSource dataSource, EconomyCatalog catalog,
                                         EconomyEngineConfig.Valuation config) {
        this.runId = Objects.requireNonNull(runId); this.dataSource = Objects.requireNonNull(dataSource);
        this.catalog = Objects.requireNonNull(catalog); this.memory = Duration.parse(config.observationMemory);
        this.minimumObservations = config.minimumObservedListings;
        this.catalogMarkupBasisPoints = config.catalogAnchorMarkupBasisPoints;
        this.overrides = new HashMap<>();
        config.customOverrides.forEach(value -> overrides.put(value.itemId,
                new ValueOverride(value.unitValueMesos, value.reason)));
    }

    @Override
    public Valuation value(String agentId, int itemId, Instant logicalAt) {
        if (agentId == null || agentId.isBlank() || itemId <= 0 || logicalAt == null)
            throw new IllegalArgumentException("valuation query is invalid");
        ArrayList<Long> observations = observations(agentId, itemId, logicalAt);
        long median = median(observations);
        long catalogAnchor = catalog.item(itemId).map(fact -> anchor(fact.npcUnitSalePrice())).orElse(0L);
        ValueOverride override = overrides.get(itemId);
        Valuation result;
        if (override != null) result = new Valuation(override.unitValueMesos,
                Valuation.Source.CUSTOM_OVERRIDE, median, observations.size(), catalogAnchor, override.reason);
        else if (observations.size() >= minimumObservations) result = new Valuation(median,
                Valuation.Source.PRIVATE_OBSERVATIONS, median, observations.size(), catalogAnchor, "");
        else if (catalogAnchor > 0) result = new Valuation(catalogAnchor,
                Valuation.Source.CATALOG_ANCHOR, median, observations.size(), catalogAnchor, "");
        else result = new Valuation(0, Valuation.Source.UNKNOWN, median, observations.size(), 0, "");
        record(agentId, itemId, logicalAt, result);
        return result;
    }

    private ArrayList<Long> observations(String agentId, int itemId, Instant at) {
        String sql = "SELECT unit_price FROM market_observation WHERE run_id=? AND agent_id=? "
                + "AND item_id=? AND observed_state='LISTED' AND logical_time>=? AND logical_time<=? "
                + "ORDER BY unit_price";
        ArrayList<Long> result = new ArrayList<>();
        try (var connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, runId); statement.setString(2, agentId); statement.setInt(3, itemId);
            statement.setTimestamp(4, Timestamp.from(at.minus(memory))); statement.setTimestamp(5, Timestamp.from(at));
            try (ResultSet rows = statement.executeQuery()) { while (rows.next()) result.add(rows.getLong(1)); }
            return result;
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not read private item knowledge", failure);
        }
    }

    private void record(String agentId, int itemId, Instant at, Valuation value) {
        String sql = "INSERT INTO item_valuation_query (valuation_id,run_id,agent_id,logical_time,item_id,"
                + "unit_value_mesos,source,observed_median_mesos,observation_count,catalog_anchor_mesos,override_reason) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (var connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, runId); statement.setString(3, agentId);
            statement.setTimestamp(4, Timestamp.from(at)); statement.setInt(5, itemId);
            statement.setLong(6, value.unitValueMesos()); statement.setString(7, value.source().name());
            statement.setLong(8, value.observedMedianMesos()); statement.setInt(9, value.observationCount());
            statement.setLong(10, value.catalogAnchorMesos()); statement.setString(11, value.overrideReason());
            statement.executeUpdate();
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not journal item valuation", failure);
        }
    }

    private long anchor(long npc) {
        if (npc <= 0) return 0;
        try { return Math.max(1, Math.multiplyExact(npc, 10_000L + catalogMarkupBasisPoints) / 10_000L); }
        catch (ArithmeticException overflow) { return Long.MAX_VALUE; }
    }

    private static long median(ArrayList<Long> values) {
        if (values.isEmpty()) return 0;
        int middle = values.size() / 2;
        if (values.size() % 2 == 1) return values.get(middle);
        return values.get(middle - 1) / 2 + values.get(middle) / 2
                + (values.get(middle - 1) % 2 + values.get(middle) % 2) / 2;
    }

    private record ValueOverride(long unitValueMesos, String reason) { }
}
