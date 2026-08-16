package server.agents.economy.market;

import java.time.Instant;
import java.util.List;

public record PhysicalStall(String stallId, String ownerAgentId, int roomMapId, int spotX,
                            Status status, Instant openedAt, List<MarketListing> listings) {
    public enum Status { WALKING_TO_SPOT, OPEN, CLOSED }

    public PhysicalStall {
        if (stallId == null || stallId.isBlank() || ownerAgentId == null || ownerAgentId.isBlank()
                || roomMapId < 910000001 || roomMapId > 910000022 || openedAt == null || status == null) {
            throw new IllegalArgumentException("invalid physical stall");
        }
        listings = listings == null ? List.of() : List.copyOf(listings);
    }
}
