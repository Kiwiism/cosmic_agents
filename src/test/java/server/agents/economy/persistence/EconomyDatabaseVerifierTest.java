package server.agents.economy.persistence;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EconomyDatabaseVerifierTest {
    @Test
    void rejectsConnectionToDatabaseOtherThanImmutableRunConfiguration() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.getCatalog()).thenReturn("wrong_database");

        assertThrows(IllegalStateException.class,
                () -> new EconomyDatabaseVerifier(dataSource).verify("cosmic_economy"));
    }
}
