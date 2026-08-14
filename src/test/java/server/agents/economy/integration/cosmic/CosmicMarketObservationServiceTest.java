package server.agents.economy.integration.cosmic;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.economy.market.MarketObservation;
import server.agents.economy.market.PrivateMarketKnowledge;
import server.agents.economy.persistence.EconomyEvidenceJournal;
import server.maps.PlayerShop;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CosmicMarketObservationServiceTest {
    @Test
    void recordsOnlyObservedListingFactsWithExactBundleIdentity() {
        UUID runId = UUID.randomUUID();
        AgentFreeMarketBuyerService buyer = mock(AgentFreeMarketBuyerService.class);
        EconomyEvidenceJournal journal = mock(EconomyEvidenceJournal.class);
        Character agent = mock(Character.class);
        Instant at = Instant.parse("2026-01-01T01:00:00Z");
        var listing = new PlayerShop.ListingView(3, 4000031, (short) 7, (short) 4,
                101, "fingerprint", Map.of("quality", "ordinary"));
        when(buyer.observeNearby(agent)).thenReturn(List.of(new AgentFreeMarketBuyerService.ObservedStall(
                90, 12, "seller", 910000004, 25, "escrow-1", List.of(listing))));
        PrivateMarketKnowledge knowledge = new PrivateMarketKnowledge();
        CosmicMarketObservationService service = new CosmicMarketObservationService(runId, buyer, journal);

        var offers = service.inspectNearby(agent, "agent-1", at, knowledge);

        assertEquals(1, offers.size());
        MarketObservation observation = offers.getFirst().observation();
        assertEquals("escrow-1:3", observation.listingId());
        assertEquals(28, observation.quantity());
        assertEquals(15, observation.unitPrice());
        assertEquals(101, observation.bundlePrice());
        assertEquals("fingerprint", observation.fingerprint());
        verify(journal).appendObservation(runId, observation);
        assertEquals(observation, knowledge.snapshot().getLast());
    }

    @Test
    void rejectsCrossAgentOrCrossRoomUseOfPrivateKnowledge() {
        AgentFreeMarketBuyerService buyer = mock(AgentFreeMarketBuyerService.class);
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(910000001);
        MarketObservation observation = observation("agent-1", 910000001);
        var offer = new CosmicMarketObservationService.ObservedOffer(8, 0, observation);
        CosmicMarketObservationService service = new CosmicMarketObservationService(
                UUID.randomUUID(), buyer, mock(EconomyEvidenceJournal.class));

        assertThrows(IllegalStateException.class, () -> service.buyObserved(agent, "agent-2", offer,
                (short) 1, Instant.parse("2026-01-01T02:00:00Z"), new PrivateMarketKnowledge()));
        verifyNoInteractions(buyer);
    }

    @Test
    void successfulPurchaseRecordsCompletedObservationWithoutChangingObservedPrice() {
        UUID runId = UUID.randomUUID();
        AgentFreeMarketBuyerService buyer = mock(AgentFreeMarketBuyerService.class);
        EconomyEvidenceJournal journal = mock(EconomyEvidenceJournal.class);
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(910000001);
        MarketObservation listed = observation("agent-1", 910000001);
        var offer = new CosmicMarketObservationService.ObservedOffer(8, 2, listed);
        when(buyer.buy(agent, 8, 2, (short) 2)).thenReturn(
                new AgentFreeMarketBuyerService.PurchaseResult(true, "SUCCESS", 22,
                        4000031, 20, -200));
        PrivateMarketKnowledge knowledge = new PrivateMarketKnowledge();
        CosmicMarketObservationService service = new CosmicMarketObservationService(runId, buyer, journal);

        var result = service.buyObserved(agent, "agent-1", offer, (short) 2,
                Instant.parse("2026-01-01T02:00:00Z"), knowledge);

        assertTrue(result.success());
        MarketObservation completed = knowledge.snapshot().getLast();
        assertEquals(MarketObservation.State.SOLD_TO_OBSERVER, completed.state());
        assertEquals(listed.unitPrice(), completed.unitPrice());
        verify(journal).appendObservation(runId, completed);
    }

    private static MarketObservation observation(String agentId, int room) {
        return new MarketObservation(UUID.randomUUID().toString(), agentId,
                Instant.parse("2026-01-01T01:00:00Z"), room, "22", "listing", 4000031,
                30, 10, 10, 3, 100, "fingerprint", Map.of(), MarketObservation.State.LISTED);
    }
}
