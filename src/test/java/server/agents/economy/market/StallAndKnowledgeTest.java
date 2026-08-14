package server.agents.economy.market;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StallAndKnowledgeTest {
    @Test
    void requiresPhysicalPresenceAndOneStallPerAgent() {
        StallRegistry registry = new StallRegistry(16);
        MarketListing listing = new MarketListing("listing", "lot", 4000000, 10, 100,
                Instant.EPOCH, EconomicReason.QUEST_REQUIREMENT);
        assertThrows(IllegalStateException.class, () -> registry.open(
                "stall", "agent", 910000001, 10, Instant.EPOCH, false, List.of(listing)));
        registry.open("stall", "agent", 910000001, 10, Instant.EPOCH, true, List.of(listing));
        assertThrows(IllegalStateException.class, () -> registry.open(
                "stall-2", "agent", 910000002, 20, Instant.EPOCH, true, List.of(listing)));
        assertEquals(1, registry.inRoom(910000001).size());
    }

    @Test
    void beliefsContainOnlyObservedListingsAndExpire() {
        PrivateMarketKnowledge knowledge = new PrivateMarketKnowledge();
        knowledge.observe(new MarketObservation("o", "buyer", Instant.EPOCH, 910000001,
                "seller", "listing", 4000000, 1, 200, MarketObservation.State.LISTED));
        assertEquals(200, knowledge.observedMedianAsk(4000000, Instant.EPOCH.plusSeconds(10),
                Duration.ofMinutes(1)));
        assertEquals(0, knowledge.observedMedianAsk(4000000, Instant.EPOCH.plusSeconds(120),
                Duration.ofMinutes(1)));
    }
}
