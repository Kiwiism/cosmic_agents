package server.agents.economy.market;

import java.time.Instant;
import java.util.UUID;

/** Private post-bid agreement. Physical exact-item settlement remains a separate step. */
public record PrivateTradeArrangement(UUID arrangementId, UUID runId, UUID offerId,
                                      String buyerAgentId, String sellerAgentId,
                                      String stallId, String listingId, int roomMapId,
                                      int itemId, String itemFingerprint, int quantity,
                                      long agreedMesos, Instant createdAt, Instant expiresAt,
                                      Status status) {
    public enum Status {
        PENDING_MEETUP, EXECUTED, EXPIRED, CANCELLED_LISTING_CHANGED, CANCELLED_PARTICIPANT
    }

    public PrivateTradeArrangement {
        if (arrangementId == null || runId == null || offerId == null
                || buyerAgentId == null || buyerAgentId.isBlank()
                || sellerAgentId == null || sellerAgentId.isBlank()
                || buyerAgentId.equals(sellerAgentId) || stallId == null || stallId.isBlank()
                || listingId == null || listingId.isBlank() || roomMapId < 910000001
                || roomMapId > 910000022 || itemId <= 0 || quantity <= 0 || agreedMesos <= 0
                || createdAt == null || expiresAt == null || !expiresAt.isAfter(createdAt)
                || status == null) {
            throw new IllegalArgumentException("invalid private trade arrangement");
        }
        itemFingerprint = itemFingerprint == null ? "" : itemFingerprint;
    }
}
