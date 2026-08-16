package server.agents.economy.market;

import java.time.Instant;

public record MarketObservation(String observationId, String observerAgentId, Instant observedAt,
                                int roomMapId, String stallOwnerAgentId, String listingId,
                                int itemId, int quantity, long unitPrice, int quantityPerBundle,
                                int bundles, long bundlePrice, String fingerprint,
                                java.util.Map<String, Object> attributes, State state) {
    public enum State { LISTED, MISSING, SOLD_TO_OBSERVER }

    public MarketObservation {
        if (observationId == null || observationId.isBlank() || observerAgentId == null
                || observerAgentId.isBlank() || observedAt == null || roomMapId < 910000001
                || roomMapId > 910000022 || stallOwnerAgentId == null || listingId == null
                || itemId <= 0 || quantity < 0 || unitPrice <= 0 || quantityPerBundle <= 0
                || bundles <= 0 || bundlePrice <= 0 || state == null) {
            throw new IllegalArgumentException("invalid market observation");
        }
        fingerprint = fingerprint == null ? "" : fingerprint;
        attributes = attributes == null ? java.util.Map.of() : java.util.Map.copyOf(attributes);
    }

    public MarketObservation(String observationId, String observerAgentId, Instant observedAt,
                             int roomMapId, String stallOwnerAgentId, String listingId,
                             int itemId, int quantity, long unitPrice, int quantityPerBundle,
                             int bundles, long bundlePrice, State state) {
        this(observationId, observerAgentId, observedAt, roomMapId, stallOwnerAgentId, listingId,
                itemId, quantity, unitPrice, quantityPerBundle, bundles, bundlePrice, "",
                java.util.Map.of(), state);
    }

    public MarketObservation(String observationId, String observerAgentId, Instant observedAt,
                             int roomMapId, String stallOwnerAgentId, String listingId,
                             int itemId, int quantity, long unitPrice, State state) {
        this(observationId, observerAgentId, observedAt, roomMapId, stallOwnerAgentId, listingId,
                itemId, quantity, unitPrice, Math.max(1, quantity), 1,
                Math.multiplyExact(unitPrice, Math.max(1, quantity)), "", java.util.Map.of(), state);
    }
}
