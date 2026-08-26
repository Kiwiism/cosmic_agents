package server.agents.capabilities.partyquest.lpq;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Session-owned reservations for LPQ split rooms. */
public final class AgentLpqRoomAssignment {
    public record ExpiredReservation(int roomMapId, int characterId) { }

    private final Map<Integer, Integer> memberByRoom = new LinkedHashMap<>();
    private final Map<Integer, Long> progressAtByRoom = new LinkedHashMap<>();
    private final Set<Integer> completedRooms = new LinkedHashSet<>();

    public synchronized boolean reserve(int roomMapId, int characterId, long nowMs) {
        if (roomMapId <= 0 || characterId <= 0 || nowMs < 0L) {
            throw new IllegalArgumentException("valid LPQ room reservation values are required");
        }
        if (completedRooms.contains(roomMapId)) return false;
        Integer owner = memberByRoom.get(roomMapId);
        if (owner != null && owner != characterId) return false;
        memberByRoom.put(roomMapId, characterId);
        progressAtByRoom.put(roomMapId, nowMs);
        return true;
    }

    public synchronized void markProgress(int roomMapId, long nowMs) {
        if (memberByRoom.containsKey(roomMapId)) progressAtByRoom.put(roomMapId, nowMs);
    }

    public synchronized void release(int roomMapId) {
        memberByRoom.remove(roomMapId);
        progressAtByRoom.remove(roomMapId);
    }

    public synchronized void complete(int roomMapId) {
        release(roomMapId);
        completedRooms.add(roomMapId);
    }

    public synchronized List<ExpiredReservation> releaseExpired(long nowMs, long leaseMs) {
        java.util.ArrayList<ExpiredReservation> expired = new java.util.ArrayList<>();
        memberByRoom.entrySet().removeIf(entry -> {
            long progressAt = progressAtByRoom.getOrDefault(entry.getKey(), 0L);
            if (nowMs - progressAt < Math.max(1L, leaseMs)) return false;
            expired.add(new ExpiredReservation(entry.getKey(), entry.getValue()));
            progressAtByRoom.remove(entry.getKey());
            return true;
        });
        return List.copyOf(expired);
    }

    public synchronized Integer owner(int roomMapId) { return memberByRoom.get(roomMapId); }
    public synchronized boolean completed(int roomMapId) { return completedRooms.contains(roomMapId); }
    public synchronized Set<Integer> completedRooms() { return Set.copyOf(completedRooms); }
    public synchronized Map<Integer, Integer> assignments() { return Map.copyOf(memberByRoom); }
    public synchronized void reset() {
        memberByRoom.clear();
        progressAtByRoom.clear();
        completedRooms.clear();
    }
}
