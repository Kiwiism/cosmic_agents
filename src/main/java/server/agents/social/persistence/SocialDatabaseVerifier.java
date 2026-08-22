package server.agents.social.persistence;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/** Verifies the dedicated PostgreSQL schema before social persistence is used. */
public final class SocialDatabaseVerifier {
    private final DataSource dataSource;

    public SocialDatabaseVerifier(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    public void verify() {
        List<Column> required = List.of(
                new Column("social_schema_version", "version"),
                new Column("agent_relationship_memory", "revision"),
                new Column("agent_conversation_turn", "expires_at_ms"),
                new Column("agent_memory_event", "salience"));
        try (Connection connection = dataSource.getConnection()) {
            if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
                throw new IllegalStateException("social memory database must be PostgreSQL");
            }
            for (Column column : required) {
                try (ResultSet result = connection.getMetaData().getColumns(
                        null, "public", column.table(), column.column())) {
                    if (!result.next()) {
                        throw new IllegalStateException("social schema is missing "
                                + column.table() + '.' + column.column() + "; apply social V001");
                    }
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Could not verify social memory database schema", failure);
        }
    }

    private record Column(String table, String column) {
    }
}
