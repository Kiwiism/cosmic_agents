package server.agents.capabilities.partyquest.ppq;

import scripting.event.EventInstanceManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Independent PPQ aggregate; no state is shared with another PQ implementation. */
public final class AgentPpqSession {
    public enum Mode { TEST_OBSERVATION, AUTONOMOUS, HUMAN_MEMBER, HUMAN_LEADER }
    public enum Phase { PREPARING, ENTERING, ACTIVE, CLEARING, EXITING, COMPLETED, FAILED }
    private final String sessionId = UUID.randomUUID().toString();
    private final Mode mode;
    private final long seed;
    private final int operatorId;
    private final boolean skipChestRooms;
    private final long startedAtMs;
    private final Map<Integer, AgentPpqMemberState> members = new LinkedHashMap<>();
    private Phase phase = Phase.PREPARING;
    private int eventLeaderId;
    private int executionAgentId;
    private long executionLeaseUntilMs;
    private long lastProgressAtMs;
    private boolean chestOneComplete;
    private boolean chestTwoComplete;
    private boolean paused;
    private boolean terminating;
    private String failure = "";
    private EventInstanceManager eventInstance;

    public AgentPpqSession(Mode mode, long seed, int operatorId, boolean skipChestRooms, long nowMs) {
        if (mode == null || operatorId <= 0 || nowMs < 0L) throw new IllegalArgumentException("valid PPQ session required");
        this.mode = mode; this.seed = seed; this.operatorId = operatorId;
        this.skipChestRooms = skipChestRooms; this.startedAtMs = nowMs; this.lastProgressAtMs = nowMs;
    }
    public synchronized void addMember(int id, AgentPpqMemberState.MemberType type) {
        if (members.size() >= AgentPpqDefinition.PARTY_SIZE
                || members.putIfAbsent(id, new AgentPpqMemberState(id, type)) != null) {
            throw new IllegalStateException("invalid or duplicate PPQ member");
        }
    }
    public synchronized void setLeadership(int leaderId, int executionId) {
        AgentPpqMemberState executor = members.get(executionId);
        if (!members.containsKey(leaderId) || executor == null
                || executor.memberType() != AgentPpqMemberState.MemberType.AGENT) {
            throw new IllegalArgumentException("PPQ leader and Agent executor must be members");
        }
        eventLeaderId = leaderId; executionAgentId = executionId;
    }
    public synchronized boolean claimExecutionTick(int id, long nowMs, long leaseMs) {
        AgentPpqMemberState member = members.get(id);
        if (member == null || member.memberType() != AgentPpqMemberState.MemberType.AGENT) return false;
        if (id != executionAgentId && nowMs < executionLeaseUntilMs) return false;
        executionAgentId = id; executionLeaseUntilMs = nowMs + Math.max(250L, leaseMs); return true;
    }
    public synchronized void transition(Phase next, long nowMs) {
        if (next == null || terminal() || next.ordinal() < phase.ordinal()) return;
        phase = next; markProgress(nowMs);
    }
    public synchronized void markChestComplete(int mapId, long nowMs) {
        boolean changed = false;
        if (mapId == AgentPpqDefinition.CHEST_ONE_MAP && !chestOneComplete) {
            chestOneComplete = true; changed = true;
        }
        if (mapId == AgentPpqDefinition.CHEST_TWO_MAP && !chestTwoComplete) {
            chestTwoComplete = true; changed = true;
        }
        if (changed) markProgress(nowMs);
    }
    public synchronized boolean chestCompleteForDeck(int mapId) {
        return mapId == AgentPpqDefinition.DECK_ONE_MAP ? chestOneComplete
                : mapId == AgentPpqDefinition.DECK_TWO_MAP && chestTwoComplete;
    }
    public synchronized boolean chestComplete(int mapId) {
        return mapId == AgentPpqDefinition.CHEST_ONE_MAP ? chestOneComplete
                : mapId == AgentPpqDefinition.CHEST_TWO_MAP && chestTwoComplete;
    }
    public synchronized void markProgress(long nowMs) { lastProgressAtMs = Math.max(lastProgressAtMs, nowMs); }
    public synchronized void fail(String reason, long nowMs) { failure = reason; phase = Phase.FAILED; markProgress(nowMs); }
    public synchronized void complete(long nowMs) { phase = Phase.COMPLETED; markProgress(nowMs); }
    public synchronized boolean terminal() { return phase == Phase.COMPLETED || phase == Phase.FAILED; }
    public synchronized boolean beginTermination() { if (terminating) return false; terminating = true; return true; }
    public String sessionId() { return sessionId; }
    public Mode mode() { return mode; }
    public long seed() { return seed; }
    public int operatorId() { return operatorId; }
    public boolean skipChestRooms() { return skipChestRooms; }
    public long startedAtMs() { return startedAtMs; }
    public synchronized Phase phase() { return phase; }
    public synchronized int eventLeaderId() { return eventLeaderId; }
    public synchronized int executionAgentId() { return executionAgentId; }
    public synchronized long lastProgressAtMs() { return lastProgressAtMs; }
    public synchronized boolean paused() { return paused; }
    public synchronized void setPaused(boolean value) { paused = value; }
    public synchronized String failure() { return failure; }
    public synchronized EventInstanceManager eventInstance() { return eventInstance; }
    public synchronized void bindEventInstance(EventInstanceManager value) { eventInstance = value; }
    public synchronized void clearEventInstance() { eventInstance = null; }
    public synchronized AgentPpqMemberState member(int id) { return members.get(id); }
    public synchronized Collection<AgentPpqMemberState> members() { return List.copyOf(members.values()); }
    public synchronized int memberCount() { return members.size(); }
}
