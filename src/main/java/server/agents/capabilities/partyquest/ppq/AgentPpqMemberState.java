package server.agents.capabilities.partyquest.ppq;

import java.util.HashSet;
import java.util.Set;

/** Mutable work state owned by one PPQ participant. */
public final class AgentPpqMemberState {
    public enum MemberType { AGENT, HUMAN }
    private final int characterId;
    private final MemberType memberType;
    private long nextActionAtMs;
    private int committedReactorObjectId;
    private int pendingChestMapId;
    private final Set<String> announcements = new HashSet<>();

    AgentPpqMemberState(int characterId, MemberType memberType) {
        if (characterId <= 0 || memberType == null) throw new IllegalArgumentException("valid PPQ member required");
        this.characterId = characterId;
        this.memberType = memberType;
    }
    public int characterId() { return characterId; }
    public MemberType memberType() { return memberType; }
    public synchronized long nextActionAtMs() { return nextActionAtMs; }
    public synchronized void deferUntil(long value) { nextActionAtMs = Math.max(0L, value); }
    public synchronized int committedReactorObjectId() { return committedReactorObjectId; }
    public synchronized void commitReactor(int id) { committedReactorObjectId = Math.max(0, id); }
    public synchronized void clearReactor() { committedReactorObjectId = 0; }
    public synchronized boolean chestDropPending(int mapId) { return pendingChestMapId == mapId; }
    public synchronized void beginChestDrop(int mapId) { pendingChestMapId = mapId; }
    public synchronized void clearChestDrop() { pendingChestMapId = 0; }
    public synchronized boolean claimAnnouncement(String key) { return announcements.add(key); }
}
