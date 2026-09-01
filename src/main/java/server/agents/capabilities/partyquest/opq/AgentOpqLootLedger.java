package server.agents.capabilities.partyquest.opq;

import java.util.LinkedHashMap;
import java.util.Map;

/** Exclusive quest-item custody. Ground ownership never overrides this ledger. */
public final class AgentOpqLootLedger {
    public enum State { RESERVED, PICKED_UP, DELIVERED }
    public record Reservation(int itemId, int ownerId, int sourceMapId, State state, long progressAtMs) { }
    private final Map<Integer, Reservation> byItem = new LinkedHashMap<>();

    public synchronized boolean reserve(int itemId, int ownerId, int sourceMapId, long nowMs) {
        if (!AgentOpqDefinition.EXCLUSIVE_ITEMS.contains(itemId) || ownerId <= 0
                || sourceMapId <= 0 || nowMs < 0L) throw new IllegalArgumentException("valid OPQ loot reservation required");
        Reservation current = byItem.get(itemId);
        if (current != null && current.state() != State.DELIVERED && current.ownerId() != ownerId) return false;
        byItem.put(itemId, new Reservation(itemId, ownerId, sourceMapId, State.RESERVED, nowMs));
        return true;
    }

    public synchronized boolean canLoot(int itemId, int characterId, int mapId) {
        Reservation reservation = byItem.get(itemId);
        return reservation != null && reservation.ownerId() == characterId
                && reservation.sourceMapId() == mapId && reservation.state() == State.RESERVED;
    }

    public synchronized boolean pickedUp(int itemId, int characterId, long nowMs) {
        Reservation reservation = byItem.get(itemId);
        if (reservation == null || reservation.ownerId() != characterId
                || reservation.state() != State.RESERVED) return false;
        byItem.put(itemId, new Reservation(itemId, characterId, reservation.sourceMapId(), State.PICKED_UP, nowMs));
        return true;
    }

    public synchronized boolean delivered(int itemId, int characterId, long nowMs) {
        Reservation reservation = byItem.get(itemId);
        if (reservation == null || reservation.ownerId() != characterId
                || reservation.state() == State.DELIVERED) return false;
        byItem.put(itemId, new Reservation(itemId, characterId, reservation.sourceMapId(), State.DELIVERED, nowMs));
        return true;
    }

    public synchronized Reservation reservation(int itemId) { return byItem.get(itemId); }
    public synchronized Map<Integer, Reservation> snapshot() { return Map.copyOf(byItem); }
}
