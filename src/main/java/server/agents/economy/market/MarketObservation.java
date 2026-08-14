package server.agents.economy.market;

import java.time.Instant;

public record MarketObservation(String observationId, String observerAgentId, Instant observedAt,
                                int roomMapId, String stallOwnerAgentId, String listingId,
                                int itemId, int quantity, long unitPrice, State state) {
    public enum State { LISTED, MISSING, SOLD_TO_OBSERVER }

    public MarketObservation {
        if (observationId == null || observationId.isBlank() || observerAgentId == null
                || observerAgentId.isBlank() || observedAt == null || roomMapId < 910000001
                || roomMapId > 910000022 || stallOwnerAgentId == null || listingId == null
                || itemId <= 0 || quantity < 0 || unitPrice <= 0 || state == null) {
            throw new IllegalArgumentException("invalid market observation");
        }
    }
}
