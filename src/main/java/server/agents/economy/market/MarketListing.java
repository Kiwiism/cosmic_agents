package server.agents.economy.market;

import java.time.Instant;

public record MarketListing(String listingId, String lotId, int itemId, int quantity,
                            long unitPrice, Instant listedAt, EconomicReason reason) {
    public MarketListing {
        if (listingId == null || listingId.isBlank() || lotId == null || lotId.isBlank()
                || itemId <= 0 || quantity <= 0 || unitPrice <= 0 || listedAt == null || reason == null) {
            throw new IllegalArgumentException("invalid market listing");
        }
    }
}
