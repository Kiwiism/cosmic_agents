package server.agents.economy.market;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Structured economic intent; public stall chat is only its human-readable rendering. */
public record StallOffer(UUID offerId, UUID runId, String buyerAgentId, String sellerAgentId,
                         String stallId, String listingId, int roomMapId, int itemId,
                         String itemFingerprint, Map<String, Object> itemAttributes,
                         int quantity, long askMesos, long offeredMesos, String publicText,
                         Instant createdAt, Instant expiresAt, Status status) {
    public enum Status {
        PENDING, ACCEPTED_AWAITING_SETTLEMENT, REJECTED, EXPIRED,
        CANCELLED_LISTING_CHANGED, EXECUTED, FAILED
    }

    public StallOffer {
        if (offerId == null || runId == null || buyerAgentId == null || buyerAgentId.isBlank()
                || sellerAgentId == null || sellerAgentId.isBlank() || buyerAgentId.equals(sellerAgentId)
                || stallId == null || stallId.isBlank() || listingId == null || listingId.isBlank()
                || roomMapId < 910000001 || roomMapId > 910000022 || itemId <= 0
                || quantity <= 0 || askMesos <= 0 || offeredMesos <= 0
                || publicText == null || publicText.isBlank() || createdAt == null || expiresAt == null
                || !expiresAt.isAfter(createdAt) || status == null) {
            throw new IllegalArgumentException("invalid stall offer");
        }
        itemFingerprint = itemFingerprint == null ? "" : itemFingerprint;
        itemAttributes = itemAttributes == null ? Map.of() : Map.copyOf(itemAttributes);
    }
}
