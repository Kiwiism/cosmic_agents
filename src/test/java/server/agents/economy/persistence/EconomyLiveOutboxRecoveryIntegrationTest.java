package server.agents.economy.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.PreparedStatement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "ECONOMY_LIVE_RECOVERY_RUN_ID", matches = ".+")
class EconomyLiveOutboxRecoveryIntegrationTest {
    @Test
    void rejectedLiveReceiptIsMaterializedIdempotentlyAfterContractFix() throws Exception {
        UUID runId = UUID.fromString(System.getenv("ECONOMY_LIVE_RECOVERY_RUN_ID"));
        try (HikariDataSource dataSource = EconomyPostgresDataSource.fromEnvironment()) {
            var result = new JdbcCosmicEconomicEventIngestor(dataSource).ingest(runId, 256);
            assertEquals(0, result.quarantined());
            assertEquals(1, count(dataSource, runId,
                    "SELECT count(*) FROM market_stall WHERE run_id = ?"));
            assertEquals(2, count(dataSource, runId,
                    "SELECT count(*) FROM market_listing WHERE run_id = ?"));
        }
    }

    private static int count(HikariDataSource dataSource, UUID runId, String sql) throws Exception {
        try (var connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, runId);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }
}
