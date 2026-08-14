package server.agents.economy.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyOutboxRelayTest {
    @Test
    void marksOnlyAcceptedRowsPublishedAndRetainsFailures() {
        var good = record("good");
        var bad = record("bad");
        StubSource source = new StubSource(List.of(good, bad));
        EconomyOutboxRelay relay = new EconomyOutboxRelay(source, row -> {
            if (row == bad) throw new IllegalStateException("database unavailable");
        });

        var result = relay.relay(10);

        assertEquals(1, result.delivered());
        assertEquals(List.of(good.outboxId()), source.published);
        assertEquals(List.of(bad.outboxId()), source.failed);
    }

    private static CosmicOutboxRecord record(String key) {
        return new CosmicOutboxRecord(UUID.randomUUID(), key, "SHOP_BUY", 1, null, key, Instant.EPOCH);
    }

    private static final class StubSource implements CosmicOutboxSource {
        private final List<CosmicOutboxRecord> records;
        private final List<UUID> published = new ArrayList<>();
        private final List<UUID> failed = new ArrayList<>();
        private StubSource(List<CosmicOutboxRecord> records) { this.records = records; }
        public List<CosmicOutboxRecord> pending(int limit) { return records; }
        public void markPublished(UUID outboxId) { published.add(outboxId); }
        public void markFailed(UUID outboxId, String error) { failed.add(outboxId); }
    }
}
