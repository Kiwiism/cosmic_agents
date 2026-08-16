package server.agents.economy.integration.cosmic;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.economy.market.StallOffer;
import server.agents.economy.market.PrivateTradeArrangement;
import server.agents.economy.persistence.StallOfferStore;
import server.agents.economy.scenario.EconomyAgentProfile;
import server.maps.PlayerShop;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CosmicStallOfferReviewServiceTest {
    private final UUID runId = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private final Instant now = Instant.parse("2026-01-01T00:10:00Z");

    @Test
    void acceptsFromStructuredAmountAndExactListingWithoutReadingFlavorText() {
        RecordingStore store = new RecordingStore(offer(390_000, now.plusSeconds(60),
                "this text intentionally contains no price"));
        Character seller = sellerWith(new PlayerShop.ListingView(3, 1302013,
                (short) 1, (short) 1, 400_000, "exact-kfan", Map.of("watk", 50)));
        CosmicStallOfferReviewService service = new CosmicStallOfferReviewService(runId, store,
                (itemId, quantity) -> 50_000);

        var result = service.reviewNext(seller, profile(.5), now);

        assertTrue(result.attempted()); assertTrue(result.accepted());
        assertEquals("ARRANGEMENT_PENDING", result.outcome());
        assertEquals(StallOffer.Status.ACCEPTED_AWAITING_SETTLEMENT, store.resolvedStatus);
        assertNotNull(store.arrangement);
        assertEquals(PrivateTradeArrangement.Status.PENDING_MEETUP, store.arrangement.status());
        assertEquals("exact-kfan", store.arrangement.itemFingerprint());
        assertEquals(380_000L, result.evidence().get("reserveMesos"));
    }

    @Test
    void rejectsNumericOfferEvenWhenFlavorClaimsAHigherPrice() {
        RecordingStore store = new RecordingStore(offer(250_000, now.plusSeconds(60),
                "hey I offer 999m according to this untrusted text"));
        Character seller = sellerWith(new PlayerShop.ListingView(3, 1302013,
                (short) 1, (short) 1, 400_000, "exact-kfan", Map.of("watk", 50)));
        CosmicStallOfferReviewService service = new CosmicStallOfferReviewService(runId, store,
                (itemId, quantity) -> 50_000);

        var result = service.reviewNext(seller, profile(.5), now);

        assertFalse(result.accepted()); assertEquals("BELOW_RESERVE", result.outcome());
        assertEquals(StallOffer.Status.REJECTED, store.resolvedStatus);
    }

    @Test
    void cancelsWhenExactItemFingerprintNoLongerMatches() {
        RecordingStore store = new RecordingStore(offer(390_000, now.plusSeconds(60), "flavor"));
        Character seller = sellerWith(new PlayerShop.ListingView(3, 1302013,
                (short) 1, (short) 1, 400_000, "different-kfan", Map.of("watk", 49)));
        CosmicStallOfferReviewService service = new CosmicStallOfferReviewService(runId, store,
                (itemId, quantity) -> 50_000);

        var result = service.reviewNext(seller, profile(.5), now);

        assertEquals("LISTING_CHANGED", result.outcome());
        assertEquals(StallOffer.Status.CANCELLED_LISTING_CHANGED, store.resolvedStatus);
    }

    @Test
    void waitsFullWindowAfterNewestHighestBid() {
        StallOffer recent = offer(390_000, now.plusSeconds(60), "flavor");
        RecordingStore store = new RecordingStore(recent);
        Character seller = sellerWith(new PlayerShop.ListingView(3, 1302013,
                (short) 1, (short) 1, 400_000, "exact-kfan", Map.of("watk", 50)));
        CosmicStallOfferReviewService service = new CosmicStallOfferReviewService(runId, store,
                (itemId, quantity) -> 50_000, (character, text) -> { },
                java.time.Duration.ofSeconds(31), java.time.Duration.ofMinutes(10));

        var result = service.reviewNext(seller, profile(.5), now);

        assertFalse(result.attempted()); assertNull(store.resolvedStatus);
    }

    private Character sellerWith(PlayerShop.ListingView listing) {
        Character seller = mock(Character.class); PlayerShop shop = mock(PlayerShop.class);
        when(seller.getMapId()).thenReturn(910000001); when(seller.getPlayerShop()).thenReturn(shop);
        when(shop.isOpen()).thenReturn(true); when(shop.listingSnapshot()).thenReturn(List.of(listing));
        return seller;
    }

    private StallOffer offer(long amount, Instant expires, String text) {
        return new StallOffer(UUID.randomUUID(), runId, "buyer", "seller", "stall", "stall:3",
                910000001, 1302013, "exact-kfan", Map.of("watk", 50), 1, 400_000,
                amount, text, now.minusSeconds(30), expires, StallOffer.Status.PENDING);
    }

    private static EconomyAgentProfile profile(double negotiation) {
        return new EconomyAgentProfile("seller", "thief", .5, .5, .5, .5, .5, .5,
                24, negotiation, .5);
    }

    private static final class RecordingStore implements StallOfferStore {
        private final StallOffer pending;
        private StallOffer.Status resolvedStatus;
        private PrivateTradeArrangement arrangement;
        private RecordingStore(StallOffer pending) { this.pending = pending; }
        @Override public void create(StallOffer offer) { }
        @Override public List<StallOffer> pendingForSeller(UUID runId, String sellerAgentId,
                                                           Instant asOf, int limit) {
            return resolvedStatus == null ? List.of(pending) : List.of();
        }
        @Override public void resolve(UUID offerId, StallOffer.Status status, String response,
                                      Instant respondedAt, String settlementTransactionId) {
            resolvedStatus = status;
        }
        @Override public void acceptForArrangement(StallOffer offer, PrivateTradeArrangement arrangement,
                                                   String response, Instant respondedAt) {
            this.arrangement = arrangement;
            this.resolvedStatus = StallOffer.Status.ACCEPTED_AWAITING_SETTLEMENT;
        }
    }
}
