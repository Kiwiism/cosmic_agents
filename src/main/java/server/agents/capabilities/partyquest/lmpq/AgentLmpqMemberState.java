package server.agents.capabilities.partyquest.lmpq;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Mutable LMPQ work state owned by one participant. */
public final class AgentLmpqMemberState {
    public enum MemberType { AGENT, HUMAN }
    private final int characterId;
    private final MemberType memberType;
    private int targetRoom;
    private int initialRoom;
    private int lastObservedRoom;
    private final List<Integer> route = new ArrayList<>();
    private final List<Integer> assignments = new ArrayList<>();
    private int committedReactorObjectId;
    private int markedSourceRoom;
    private int markedPortalId;
    private long nextActionAtMs;
    private final Set<String> announcements = new HashSet<>();

    AgentLmpqMemberState(int characterId, MemberType memberType) {
        if (characterId <= 0 || memberType == null) throw new IllegalArgumentException("valid LMPQ member required");
        this.characterId = characterId;
        this.memberType = memberType;
    }

    public int characterId() { return characterId; }
    public MemberType memberType() { return memberType; }
    public synchronized int targetRoom() { return targetRoom; }
    public synchronized void assignTargetRoom(int room) {
        targetRoom = room;
        if (room > 0 && (assignments.isEmpty() || assignments.get(assignments.size() - 1) != room)) {
            assignments.add(room);
        }
    }
    public synchronized void clearTargetRoom() { targetRoom = 0; committedReactorObjectId = 0; }
    public synchronized int committedReactorObjectId() { return committedReactorObjectId; }
    public synchronized void commitReactor(int objectId) { committedReactorObjectId = Math.max(0, objectId); }
    public synchronized void clearReactor() { committedReactorObjectId = 0; }
    public synchronized boolean portalMarked(int sourceRoom, int portalId) {
        return markedSourceRoom == sourceRoom && markedPortalId == portalId;
    }
    public synchronized void markPortal(int sourceRoom, int portalId) {
        markedSourceRoom = sourceRoom; markedPortalId = portalId;
    }
    public synchronized void clearPortalMarker() { markedSourceRoom = 0; markedPortalId = 0; }
    public synchronized long nextActionAtMs() { return nextActionAtMs; }
    public synchronized void deferUntil(long value) { nextActionAtMs = Math.max(0L, value); }
    public synchronized boolean claimAnnouncement(String key) {
        return key != null && !key.isBlank() && announcements.add(key);
    }
    public synchronized void observeRoom(int room) {
        if (room <= 0 || room == lastObservedRoom) return;
        if (initialRoom == 0) initialRoom = room;
        lastObservedRoom = room;
        route.add(room);
    }
    public synchronized int initialRoom() { return initialRoom; }
    public synchronized List<Integer> route() { return List.copyOf(route); }
    public synchronized List<Integer> assignments() { return List.copyOf(assignments); }
}
