package server.agents.economy.persistence;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/** Fails before a run if the dedicated PostgreSQL schema is not at the required contract. */
public final class EconomyDatabaseVerifier {
    private final DataSource dataSource;
    public EconomyDatabaseVerifier(DataSource dataSource) { this.dataSource = Objects.requireNonNull(dataSource); }

    public void verify() { verify(null); }

    public void verify(String expectedDatabase) {
        List<Column> required = List.of(
                new Column("simulation_run", "config_yaml"),
                new Column("economic_event", "evidence"),
                new Column("ledger_posting", "lot_id"),
                new Column("market_observation", "item_attributes"),
                new Column("market_listing_lot", "lot_id"),
                new Column("agent_presence_event", "reason"),
                new Column("negotiation_session", "transcript"));
        try (Connection connection = dataSource.getConnection()) {
            if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName()))
                throw new IllegalStateException("economy evidence database must be PostgreSQL");
            if (expectedDatabase != null && !expectedDatabase.equals(connection.getCatalog()))
                throw new IllegalStateException("economy database mismatch: configured="
                        + expectedDatabase + " connected=" + connection.getCatalog());
            for (Column column : required) {
                try (ResultSet result = connection.getMetaData().getColumns(
                        null, "public", column.table(), column.column())) {
                    if (!result.next()) throw new IllegalStateException("economy schema is missing "
                            + column.table() + '.' + column.column() + "; apply V001 through V010");
                }
            }
        } catch (SQLException failure) {
            throw new EconomyPersistenceException("Could not verify economy database schema", failure);
        }
    }

    private record Column(String table, String column) { }
}
