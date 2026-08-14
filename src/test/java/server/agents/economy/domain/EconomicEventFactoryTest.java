package server.agents.economy.domain;

import org.junit.jupiter.api.Test;
import server.agents.economy.market.EconomicReason;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EconomicEventFactoryTest {
    @Test
    void replayTracksMesoItemsTaxAndProvenance() {
        EconomicEventFactory factory = new EconomicEventFactory(UUID.randomUUID(), "config", "catalog");
        EconomicLedgerProjection ledger = new EconomicLedgerProjection();
        ledger.apply(factory.initialEndowment("buyer-start", Instant.EPOCH, "buyer", 10_000, Map.of()));
        ledger.apply(factory.initialEndowment("seller-start", Instant.EPOCH, "seller", 0,
                Map.of(4000000, 10)));
        ledger.apply(factory.stallListed("listing-1", Instant.EPOCH.plusMillis(500), "seller",
                "escrow-1", 910000001, "listing", 4000000, 10,
                "seller-start:4000000", EconomicReason.QUEST_REQUIREMENT));
        EconomicEvent trade = factory.stallSale("sale-1", Instant.EPOCH.plusSeconds(1),
                "buyer", "seller", "escrow-1", 910000001, "listing", 4000000, 10,
                1_000, 0, 30, "seller-start:4000000", EconomicReason.QUEST_REQUIREMENT);
        ledger.apply(trade);

        assertEquals(9_000, ledger.balance(LedgerAccount.agent("buyer"), AssetKey.MESO, ""));
        assertEquals(970, ledger.balance(LedgerAccount.agent("seller"), AssetKey.MESO, ""));
        assertEquals(10, ledger.balance(LedgerAccount.agent("buyer"), AssetKey.item(4000000),
                "seller-start:4000000"));
        assertTrue(trade.evidence().containsKey("reason"));
    }

    @Test
    void rejectsSelfTradeAndOverTax() {
        EconomicEventFactory factory = new EconomicEventFactory(UUID.randomUUID(), "config", "catalog");
        assertThrows(IllegalArgumentException.class, () -> factory.stallSale("bad", Instant.EPOCH,
                "same", "same", "escrow", 910000001, "l", 1, 1, 10, 0, 11, "lot",
                EconomicReason.SPECULATIVE_RESALE));
    }
}
