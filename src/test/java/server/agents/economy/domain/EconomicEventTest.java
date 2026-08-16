package server.agents.economy.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EconomicEventTest {
    @Test
    void acceptsBalancedMultiAssetTrade() {
        var buyer = LedgerAccount.agent("buyer");
        var seller = LedgerAccount.agent("seller");
        assertDoesNotThrow(() -> event(List.of(
                new LedgerPosting(buyer, AssetKey.MESO, -1_000, ""),
                new LedgerPosting(seller, AssetKey.MESO, 1_000, ""),
                new LedgerPosting(seller, AssetKey.item(2000000), -10, "lot-1"),
                new LedgerPosting(buyer, AssetKey.item(2000000), 10, "lot-1"))));
    }

    @Test
    void rejectsMissingProvenanceCounterposting() {
        assertThrows(IllegalArgumentException.class, () -> event(List.of(
                new LedgerPosting(LedgerAccount.agent("buyer"), AssetKey.MESO, -1_000, ""))));
    }

    private static EconomicEvent event(List<LedgerPosting> postings) {
        return new EconomicEvent(UUID.randomUUID(), UUID.randomUUID(), Instant.EPOCH,
                EconomicEventKind.DIRECT_TRADE, "key", "", "", "config", "catalog",
                List.of("buyer", "seller"), Map.of("reason", "equipment upgrade"), postings);
    }
}
