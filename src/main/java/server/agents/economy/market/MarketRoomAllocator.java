package server.agents.economy.market;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntUnaryOperator;

/**
 * Run-local, deterministic home-room allocation for active sellers.
 * Lower-numbered rooms are exhausted before the next room is used.
 */
public final class MarketRoomAllocator {
    private final int firstRoomMapId;
    private final int lastRoomMapId;
    private final IntUnaryOperator capacity;
    private final Map<String, Integer> roomBySeller = new HashMap<>();
    private final Map<Integer, Integer> assignedByRoom = new HashMap<>();

    public MarketRoomAllocator(int firstRoomMapId, int lastRoomMapId,
                               IntUnaryOperator capacity) {
        if (firstRoomMapId < 910000001 || lastRoomMapId > 910000022
                || firstRoomMapId > lastRoomMapId) {
            throw new IllegalArgumentException("invalid configured FM room range");
        }
        this.firstRoomMapId = firstRoomMapId;
        this.lastRoomMapId = lastRoomMapId;
        this.capacity = Objects.requireNonNull(capacity);
        for (int room = firstRoomMapId; room <= lastRoomMapId; room++) {
            if (capacity.applyAsInt(room) <= 0) {
                throw new IllegalArgumentException("FM room capacity must be positive: " + room);
            }
        }
    }

    public synchronized int roomFor(String sellerAgentId) {
        if (sellerAgentId == null || sellerAgentId.isBlank()) {
            throw new IllegalArgumentException("seller agent id is required");
        }
        Integer existing = roomBySeller.get(sellerAgentId);
        if (existing != null) return existing;
        for (int room = firstRoomMapId; room <= lastRoomMapId; room++) {
            int assigned = assignedByRoom.getOrDefault(room, 0);
            if (assigned >= capacity.applyAsInt(room)) continue;
            roomBySeller.put(sellerAgentId, room);
            assignedByRoom.put(room, assigned + 1);
            return room;
        }
        throw new IllegalStateException("all configured Free Market rooms are assigned");
    }

    public synchronized void release(String sellerAgentId) {
        Integer room = sellerAgentId == null ? null : roomBySeller.remove(sellerAgentId);
        if (room == null) return;
        int remaining = assignedByRoom.getOrDefault(room, 1) - 1;
        if (remaining <= 0) assignedByRoom.remove(room);
        else assignedByRoom.put(room, remaining);
    }

    public synchronized int assignedTo(int roomMapId) {
        return assignedByRoom.getOrDefault(roomMapId, 0);
    }
}
