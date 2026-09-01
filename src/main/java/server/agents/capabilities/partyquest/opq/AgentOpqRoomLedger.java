package server.agents.capabilities.partyquest.opq;

import java.util.EnumMap;
import java.util.Map;

/** Session-owned room leases. A worker may heartbeat only its own reservation. */
public final class AgentOpqRoomLedger {
    public enum State { UNCLAIMED, CLAIMED, ENTERED, OBJECTIVE_DONE, COMPLETE }
    public record Lease(AgentOpqDefinition.Room room, int ownerId, State state, long progressAtMs) { }
    private final EnumMap<AgentOpqDefinition.Room, Lease> leases =
            new EnumMap<>(AgentOpqDefinition.Room.class);

    public synchronized boolean claim(AgentOpqDefinition.Room room, int ownerId, long nowMs) {
        if (room == null || ownerId <= 0 || nowMs < 0L) throw new IllegalArgumentException("valid OPQ room claim required");
        Lease current = leases.get(room);
        if (current != null && current.state() == State.COMPLETE) return false;
        if (current != null && current.ownerId() != ownerId) return false;
        leases.put(room, new Lease(room, ownerId,
                current == null ? State.CLAIMED : current.state(), nowMs));
        return true;
    }

    public synchronized boolean advance(AgentOpqDefinition.Room room, int ownerId,
                                        State state, long nowMs) {
        Lease current = leases.get(room);
        if (current == null || current.ownerId() != ownerId || state == null
                || state.ordinal() < current.state().ordinal()) return false;
        leases.put(room, new Lease(room, ownerId, state, nowMs));
        return true;
    }

    public synchronized void heartbeat(AgentOpqDefinition.Room room, int ownerId, long nowMs) {
        Lease current = leases.get(room);
        if (current != null && current.ownerId() == ownerId && current.state() != State.COMPLETE) {
            leases.put(room, new Lease(room, ownerId, current.state(), nowMs));
        }
    }

    public synchronized boolean releaseExpired(long nowMs, long leaseMs) {
        boolean[] released = {false};
        leases.entrySet().removeIf(entry -> {
            Lease lease = entry.getValue();
            boolean expire = lease.state() != State.COMPLETE
                    && nowMs - lease.progressAtMs() >= Math.max(1L, leaseMs);
            released[0] |= expire;
            return expire;
        });
        return released[0];
    }

    public synchronized Lease lease(AgentOpqDefinition.Room room) { return leases.get(room); }
    public synchronized boolean complete(AgentOpqDefinition.Room room) {
        Lease lease = leases.get(room);
        return lease != null && lease.state() == State.COMPLETE;
    }
    public synchronized boolean allComplete() {
        for (AgentOpqDefinition.Room room : AgentOpqDefinition.Room.values()) if (!complete(room)) return false;
        return true;
    }
    public synchronized Map<AgentOpqDefinition.Room, Lease> snapshot() { return Map.copyOf(leases); }
}
