package server.agents.economy.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcCosmicEconomicEventIngestorTest {
    @Test
    void quarantineReceiptUsesStableStringTimestamps() throws Exception {
        UUID outboxId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Instant logicalAt = Instant.parse("2026-01-01T01:34:05Z");
        Instant createdAt = Instant.parse("2026-08-15T00:00:00Z");
        CosmicOutboxRecord receipt = new CosmicOutboxRecord(outboxId, "shop:list", "PLAYER_SHOP_LIST",
                146, null, "opened shop", "{}", runId, logicalAt, "decision-1", null,
                "config", "catalog", "MARKET_CYCLE", true, false, createdAt);

        var json = new ObjectMapper().readTree(
                JdbcCosmicEconomicEventIngestor.quarantineReceiptJson(receipt));

        assertEquals(outboxId.toString(), json.path("outboxId").asText());
        assertEquals(runId.toString(), json.path("runId").asText());
        assertEquals(logicalAt.toString(), json.path("logicalAt").asText());
        assertEquals(createdAt.toString(), json.path("createdAt").asText());
    }
}
