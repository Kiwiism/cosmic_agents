package server.agents.economy.persistence;

import server.agents.economy.market.StallOffer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StallOfferStore {
    default boolean enabled() { return true; }
    void create(StallOffer offer);
    default List<StallOffer> pendingForSeller(UUID runId, String sellerAgentId, Instant asOf, int limit) {
        return List.of();
    }
    void resolve(UUID offerId, StallOffer.Status status, String response,
                 Instant respondedAt, String settlementTransactionId);

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
