package server.agents.economy.ownership;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record InventoryReview(UUID reviewId, UUID runId, String agentId, InventorySnapshot snapshot,
                              Instant logicalTime, Purpose purpose,
                              List<InventoryDispositionDecision> decisions,
                              List<AssetReservation> reservations,
                              List<ActionAuthorization> authorizations) {
    public InventoryReview {
        reviewId = Objects.requireNonNull(reviewId); runId = Objects.requireNonNull(runId);
        agentId = Objects.requireNonNull(agentId); snapshot = Objects.requireNonNull(snapshot);
        logicalTime = Objects.requireNonNull(logicalTime); purpose = Objects.requireNonNull(purpose);
        decisions = List.copyOf(decisions); reservations = List.copyOf(reservations);
        authorizations = List.copyOf(authorizations);
    }

    public enum Purpose { FM_ENTRY_SCAN, FM_MARKET_APPRAISAL }

    public record AssetReservation(UUID reservationId, InventoryItemRef item, int quantity,
                                   String action, String venue) { }

    public record ActionAuthorization(UUID authorizationId, InventoryItemRef item, int quantity,
                                      String action, String venue, String inventoryRevision,
                                      Instant expiresAt) { }
}
