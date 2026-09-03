package server.agents.capabilities.partyquest.lmpq;

import java.util.LinkedHashMap;
import java.util.Map;

/** Session-owned farm-room leases. Occupied rooms remain legal transit nodes. */
public final class AgentLmpqRoomLedger {
    public enum State { AVAILABLE, RESERVED, WORKING, HUMAN_OCCUPIED, DEPLETED }
    public record Lease(int room, int ownerId, State state, long progressAtMs) { }

    private final Map<Integer, Lease> rooms = new LinkedHashMap<>();

    public AgentLmpqRoomLedger() {
        for (int room = 1; room <= 15; room++) rooms.put(room, new Lease(room, 0, State.AVAILABLE, 0L));
    }

    public synchronized boolean reserve(int room, int ownerId, long nowMs) {
        validate(room, ownerId, nowMs);
        Lease current = rooms.get(room);
        if (current.state() == State.DEPLETED || current.state() == State.HUMAN_OCCUPIED) return false;
        if (current.ownerId() != 0 && current.ownerId() != ownerId) return false;
        rooms.put(room, new Lease(room, ownerId, State.RESERVED, nowMs));
        return true;
    }

    public synchronized boolean beginWork(int room, int ownerId, long nowMs) {
        Lease current = rooms.get(room);
        if (current == null || current.ownerId() != ownerId || current.state() == State.DEPLETED) return false;
        rooms.put(room, new Lease(room, ownerId, State.WORKING, nowMs));
        return true;
    }

    public synchronized void heartbeat(int room, int ownerId, long nowMs) {
        Lease current = rooms.get(room);
        if (current != null && current.ownerId() == ownerId
                && (current.state() == State.RESERVED || current.state() == State.WORKING)) {
            rooms.put(room, new Lease(room, ownerId, current.state(), nowMs));
        }
    }

    public synchronized void humanOccupied(int room, long nowMs) {
        if (room >= 1 && room <= 15) rooms.put(room, new Lease(room, 0, State.HUMAN_OCCUPIED, nowMs));
    }

    public synchronized void humanLeft(int room, long nowMs) {
        Lease current = rooms.get(room);
        if (current != null && current.state() == State.HUMAN_OCCUPIED) {
            rooms.put(room, new Lease(room, 0, State.AVAILABLE, nowMs));
        }
    }

    public synchronized void releaseOwner(int ownerId, long nowMs) {
        rooms.replaceAll((room, lease) -> lease.ownerId() == ownerId
                && lease.state() != State.DEPLETED
                ? new Lease(room, 0, State.AVAILABLE, nowMs) : lease);
    }

    public synchronized void depleted(int room, int ownerId, long nowMs) {
        Lease current = rooms.get(room);
        if (current != null && (current.ownerId() == ownerId || current.ownerId() == 0)) {
            rooms.put(room, new Lease(room, 0, State.DEPLETED, nowMs));
        }
    }

    public synchronized int releaseExpired(long nowMs, long leaseMs) {
        int released = 0;
        for (int room = 1; room <= 15; room++) {
            Lease lease = rooms.get(room);
            if ((lease.state() == State.RESERVED || lease.state() == State.WORKING)
                    && nowMs - lease.progressAtMs() >= Math.max(1L, leaseMs)) {
                rooms.put(room, new Lease(room, 0, State.AVAILABLE, nowMs));
                released++;
            }
        }
        return released;
    }

    public synchronized Lease room(int room) { return rooms.get(room); }
    public synchronized Map<Integer, Lease> snapshot() { return Map.copyOf(rooms); }

    private static void validate(int room, int ownerId, long nowMs) {
        if (room < 1 || room > 15 || ownerId <= 0 || nowMs < 0L) {
            throw new IllegalArgumentException("valid LMPQ room reservation required");
        }
    }
}
