package server.agents.economy.market;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Authoritative lifecycle guard for one physical PlayerShop per owner and one owner per spot. */
public final class StallRegistry {
    private final int maximumListings;
    private final Map<String, PhysicalStall> byOwner = new HashMap<>();
    private final Map<Spot, String> spots = new HashMap<>();

    public StallRegistry(int maximumListings) {
        if (maximumListings <= 0) throw new IllegalArgumentException("maximumListings must be positive");
        this.maximumListings = maximumListings;
    }

    public synchronized PhysicalStall open(String stallId, String ownerAgentId, int roomMapId,
                                           int spotX, Instant now, boolean ownerPhysicallyPresent,
                                           List<MarketListing> listings) {
        if (!ownerPhysicallyPresent) throw new IllegalStateException("Owner must walk to the stall spot");
        if (byOwner.containsKey(ownerAgentId)) throw new IllegalStateException("Agent already owns a stall");
        if (listings == null || listings.isEmpty() || listings.size() > maximumListings)
            throw new IllegalArgumentException("stall listing count is invalid");
        Spot spot = new Spot(roomMapId, spotX);
        if (spots.containsKey(spot)) throw new IllegalStateException("Stall spot is occupied");
        PhysicalStall stall = new PhysicalStall(stallId, ownerAgentId, roomMapId, spotX,
                PhysicalStall.Status.OPEN, now, listings);
        byOwner.put(ownerAgentId, stall);
        spots.put(spot, ownerAgentId);
        return stall;
    }

    public synchronized Optional<PhysicalStall> ownedBy(String ownerAgentId) {
        return Optional.ofNullable(byOwner.get(ownerAgentId));
    }

    public synchronized List<PhysicalStall> inRoom(int roomMapId) {
        return byOwner.values().stream().filter(stall -> stall.roomMapId() == roomMapId).toList();
    }

    public synchronized PhysicalStall close(String ownerAgentId, Instant now) {
        PhysicalStall stall = byOwner.remove(ownerAgentId);
        if (stall == null) throw new IllegalStateException("Agent does not own a stall");
        spots.remove(new Spot(stall.roomMapId(), stall.spotX()));
        return new PhysicalStall(stall.stallId(), stall.ownerAgentId(), stall.roomMapId(), stall.spotX(),
                PhysicalStall.Status.CLOSED, stall.openedAt(), stall.listings());
    }

    private record Spot(int roomMapId, int x) { }
}
