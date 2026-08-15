package server.agents.economy.persistence;

import server.agents.economy.market.StallOffer;
import server.agents.economy.market.PrivateTradeArrangement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface StallOfferStore {
    default boolean enabled() { return true; }
    void create(StallOffer offer);
    default Optional<StallOffer> highestPendingForListing(UUID runId, String listingId,
                                                          Instant asOf) {
        return Optional.empty();
    }
    default long committedMesosForBuyer(UUID runId, String buyerAgentId, Instant asOf) { return 0; }
    default List<StallOffer> pendingForSeller(UUID runId, String sellerAgentId, Instant asOf, int limit) {
        return List.of();
    }
    void resolve(UUID offerId, StallOffer.Status status, String response,
                 Instant respondedAt, String settlementTransactionId);
    default void acceptForArrangement(StallOffer offer, PrivateTradeArrangement arrangement,
                                      String response, Instant respondedAt) {
        resolve(offer.offerId(), StallOffer.Status.ACCEPTED_AWAITING_SETTLEMENT,
                response, respondedAt, null);
    }

    static StallOfferStore noop() {
        return new StallOfferStore() {
            @Override public boolean enabled() { return false; }
            @Override public void create(StallOffer offer) { }
            @Override public void resolve(java.util.UUID offerId, StallOffer.Status status,
                                          String response, Instant respondedAt,
                                          String settlementTransactionId) { }
        };
    }
}
