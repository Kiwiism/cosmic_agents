package server.agents.capabilities.partyquest.opq;

import scripting.event.EventInstanceManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;

/** OPQ aggregate root. All mutable party/room/item ownership is session scoped. */
public final class AgentOpqSession {
    public enum Mode { TEST_OBSERVATION, AUTONOMOUS, HUMAN_ASSISTED }
    public enum Phase {
        PREPARING, ENTERING, ENTRANCE, SPLIT_ROOMS, RESTORING_STATUE,
        GARDEN, RESTORING_MINERVA, CLAIMING_REWARD, EXITING, COMPLETED, FAILED
    }
    private final String sessionId = UUID.randomUUID().toString();
    private final Mode mode;
    private final long seed;
    private final int operatorId;
    private final long startedAtMs;
    private final Map<Integer, AgentOpqMemberState> members = new LinkedHashMap<>();
    private final AgentOpqRoomLedger rooms = new AgentOpqRoomLedger();
    private final AgentOpqLootLedger loot = new AgentOpqLootLedger();
    private final AgentOpqPortalRoute loungeRoute = new AgentOpqPortalRoute(2, 3);
    private final AgentOpqPortalRoute wayUpRoute = new AgentOpqPortalRoute(16, 4);
    private Phase phase = Phase.PREPARING;
    private int eventLeaderId;
    private int executionAgentId;
    private long executionLeaseUntilMs;
    private long lastProgressAtMs;
    private long phaseEnteredAtMs;
    private boolean paused;
    private boolean terminating;
    private String failure = "";
    private EventInstanceManager eventInstance;
    private int sealedAttempt;
    private long sealedCheckedAtMs;
    private int wayUpLeverAttempt;
    private final Set<Integer> completedLoungeSubrooms = new LinkedHashSet<>();
    private boolean papaPixieEngaged;
    private boolean papaPixieDefeated;

    public AgentOpqSession(Mode mode, long seed, int operatorId, long nowMs) {
        if (mode == null || operatorId <= 0 || nowMs < 0L) throw new IllegalArgumentException("valid OPQ session required");
        this.mode = mode;
        this.seed = seed;
        this.operatorId = operatorId;
        this.startedAtMs = nowMs;
        this.lastProgressAtMs = nowMs;
        this.phaseEnteredAtMs = nowMs;
    }

    public synchronized void addMember(int id, AgentOpqMemberState.MemberType type) {
        if (members.size() >= AgentOpqDefinition.PARTY_SIZE || members.putIfAbsent(id, new AgentOpqMemberState(id, type)) != null) {
            throw new IllegalStateException("invalid or duplicate OPQ member");
        }
    }
    public synchronized void setLeadership(int leaderId, int executionId) {
        if (!members.containsKey(leaderId) || !members.containsKey(executionId)) throw new IllegalArgumentException("OPQ leaders must be members");
        eventLeaderId = leaderId;
        executionAgentId = executionId;
    }
    public synchronized boolean claimExecutionTick(int id, long nowMs, long leaseMs) {
        if (id != executionAgentId && nowMs < executionLeaseUntilMs) return false;
        if (nowMs >= executionLeaseUntilMs || id == executionAgentId) {
            executionAgentId = id;
            executionLeaseUntilMs = nowMs + Math.max(1L, leaseMs);
            return true;
        }
        return false;
    }
    public synchronized void transition(Phase next, long nowMs) {
        if (next == null || terminal() || next.ordinal() < phase.ordinal()) return;
        if (next != phase) { phase = next; phaseEnteredAtMs = nowMs; }
        markProgress(nowMs);
    }
    public synchronized void markProgress(long nowMs) { lastProgressAtMs = Math.max(lastProgressAtMs, nowMs); }
    public synchronized boolean beginTermination() { if (terminating) return false; terminating = true; return true; }
    public synchronized void fail(String reason, long nowMs) { failure = reason == null ? "OPQ failed" : reason; phase = Phase.FAILED; markProgress(nowMs); }
    public synchronized void complete(long nowMs) { phase = Phase.COMPLETED; markProgress(nowMs); }
    public synchronized boolean terminal() { return phase == Phase.COMPLETED || phase == Phase.FAILED; }

    public String sessionId() { return sessionId; }
    public Mode mode() { return mode; }
    public long seed() { return seed; }
    public int operatorId() { return operatorId; }
    public long startedAtMs() { return startedAtMs; }
    public synchronized Phase phase() { return phase; }
    public synchronized int eventLeaderId() { return eventLeaderId; }
    public synchronized int executionAgentId() { return executionAgentId; }
    public synchronized long lastProgressAtMs() { return lastProgressAtMs; }
    public synchronized long phaseEnteredAtMs() { return phaseEnteredAtMs; }
    public synchronized boolean paused() { return paused; }
    public synchronized void setPaused(boolean value) { paused = value; }
    public synchronized String failure() { return failure; }
    public synchronized EventInstanceManager eventInstance() { return eventInstance; }
    public synchronized void bindEventInstance(EventInstanceManager value) { eventInstance = value; }
    public synchronized void clearEventInstance() { eventInstance = null; }
    public synchronized AgentOpqMemberState member(int id) { return members.get(id); }
    public synchronized Collection<AgentOpqMemberState> members() { return List.copyOf(members.values()); }
    public synchronized int memberCount() { return members.size(); }
    public AgentOpqRoomLedger rooms() { return rooms; }
    public AgentOpqLootLedger loot() { return loot; }
    public AgentOpqPortalRoute loungeRoute() { return loungeRoute; }
    public AgentOpqPortalRoute wayUpRoute() { return wayUpRoute; }
    public synchronized int sealedAttempt() { return sealedAttempt; }
    public synchronized void advanceSealedAttempt(long nowMs) { sealedAttempt = Math.min(9, sealedAttempt + 1); sealedCheckedAtMs = nowMs; markProgress(nowMs); }
    public synchronized long sealedCheckedAtMs() { return sealedCheckedAtMs; }
    public synchronized void markSealedChecked(long nowMs) { sealedCheckedAtMs = nowMs; }
    public synchronized int wayUpLeverAttempt() { return wayUpLeverAttempt; }
    public synchronized void advanceWayUpLeverAttempt(long nowMs) { wayUpLeverAttempt = Math.min(9, wayUpLeverAttempt + 1); markProgress(nowMs); }
    public synchronized void completeLoungeSubroom(int mapId, long nowMs) {
        if (AgentOpqDefinition.LOUNGE_ROOM_MAPS.contains(mapId) && completedLoungeSubrooms.add(mapId)) markProgress(nowMs);
    }
    public synchronized boolean loungeSubroomComplete(int mapId) { return completedLoungeSubrooms.contains(mapId); }
    public synchronized Set<Integer> completedLoungeSubrooms() { return Set.copyOf(completedLoungeSubrooms); }
    public synchronized void observePapaPixie(boolean alive, long nowMs) {
        if (alive) {
            if (!papaPixieEngaged) markProgress(nowMs);
            papaPixieEngaged = true;
            return;
        }
        if (papaPixieEngaged && !papaPixieDefeated) {
            papaPixieDefeated = true;
            markProgress(nowMs);
        }
    }
    public synchronized boolean papaPixieEngaged() { return papaPixieEngaged; }
    public synchronized boolean papaPixieDefeated() { return papaPixieDefeated; }
}
