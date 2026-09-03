package server.agents.capabilities.partyquest.lmpq;

import scripting.event.EventInstanceManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** LMPQ aggregate root; all routing and lease state is isolated per event. */
public final class AgentLmpqSession {
    public enum Mode { TEST_OBSERVATION, AUTONOMOUS, HUMAN_MEMBER, HUMAN_LEADER }
    public enum Phase { PREPARING, ENTERING, FARMING, REGROUPING, CLEARING, REWARD, EXITING, COMPLETED, FAILED }

    private final String sessionId = UUID.randomUUID().toString();
    private final Mode mode;
    private final long seed;
    private final int operatorId;
    private final int requestedPartySize;
    private final long startedAtMs;
    private final Map<Integer, AgentLmpqMemberState> members = new LinkedHashMap<>();
    private final AgentLmpqRoomLedger rooms = new AgentLmpqRoomLedger();
    private int rendezvousRoom = AgentLmpqDefinition.RENDEZVOUS_ROOM;
    private Phase phase = Phase.PREPARING;
    private int eventLeaderId;
    private int executionAgentId;
    private long executionLeaseUntilMs;
    private long lastProgressAtMs;
    private long phaseEnteredAtMs;
    private long progressSignature = Long.MIN_VALUE;
    private boolean paused;
    private boolean terminating;
    private String failure = "";
    private EventInstanceManager eventInstance;

    public AgentLmpqSession(Mode mode, long seed, int operatorId, int requestedPartySize, long nowMs) {
        if (mode == null || operatorId <= 0 || requestedPartySize < AgentLmpqDefinition.MIN_PARTY_SIZE
                || requestedPartySize > AgentLmpqDefinition.MAX_PARTY_SIZE || nowMs < 0L) {
            throw new IllegalArgumentException("valid LMPQ session required");
        }
        this.mode = mode;
        this.seed = seed;
        this.operatorId = operatorId;
        this.requestedPartySize = requestedPartySize;
        this.startedAtMs = nowMs;
        this.lastProgressAtMs = nowMs;
        this.phaseEnteredAtMs = nowMs;
    }

    public synchronized void addMember(int id, AgentLmpqMemberState.MemberType type) {
        if (members.size() >= requestedPartySize
                || members.putIfAbsent(id, new AgentLmpqMemberState(id, type)) != null) {
            throw new IllegalStateException("invalid or duplicate LMPQ member");
        }
    }

    public synchronized void setLeadership(int leaderId, int executionId) {
        AgentLmpqMemberState execution = members.get(executionId);
        if (!members.containsKey(leaderId) || execution == null
                || execution.memberType() != AgentLmpqMemberState.MemberType.AGENT) {
            throw new IllegalArgumentException("LMPQ event leader and Agent executor must be members");
        }
        eventLeaderId = leaderId;
        executionAgentId = executionId;
    }

    public synchronized boolean claimExecutionTick(int id, long nowMs, long leaseMs) {
        AgentLmpqMemberState member = members.get(id);
        if (member == null || member.memberType() != AgentLmpqMemberState.MemberType.AGENT) return false;
        if (id != executionAgentId && nowMs < executionLeaseUntilMs) return false;
        executionAgentId = id;
        executionLeaseUntilMs = nowMs + Math.max(250L, leaseMs);
        return true;
    }

    public synchronized void transition(Phase next, long nowMs) {
        if (next == null || terminal() || next.ordinal() < phase.ordinal()) return;
        if (next != phase) {
            phase = next;
            phaseEnteredAtMs = nowMs;
            markProgress(nowMs);
        }
    }

    public synchronized void observeProgressSignature(long signature, long nowMs) {
        if (progressSignature != signature) {
            progressSignature = signature;
            markProgress(nowMs);
        }
    }

    public synchronized void markProgress(long nowMs) { lastProgressAtMs = Math.max(lastProgressAtMs, nowMs); }
    public synchronized void fail(String reason, long nowMs) {
        failure = reason == null || reason.isBlank() ? "LMPQ failed" : reason;
        phase = Phase.FAILED;
        markProgress(nowMs);
    }
    public synchronized void complete(long nowMs) { phase = Phase.COMPLETED; markProgress(nowMs); }
    public synchronized boolean terminal() { return phase == Phase.COMPLETED || phase == Phase.FAILED; }
    public synchronized boolean beginTermination() { if (terminating) return false; terminating = true; return true; }

    public String sessionId() { return sessionId; }
    public Mode mode() { return mode; }
    public long seed() { return seed; }
    public int operatorId() { return operatorId; }
    public int requestedPartySize() { return requestedPartySize; }
    public long startedAtMs() { return startedAtMs; }
    public AgentLmpqRoomLedger rooms() { return rooms; }
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
    public synchronized int rendezvousRoom() { return rendezvousRoom; }
    public synchronized void setRendezvousRoom(int room) {
        if (room != AgentLmpqDefinition.RENDEZVOUS_ROOM && room != AgentLmpqDefinition.CLEAR_ROOM) {
            throw new IllegalArgumentException("LMPQ rendezvous room must be 9 or 16");
        }
        if (phase.ordinal() >= Phase.FARMING.ordinal()) {
            throw new IllegalStateException("LMPQ rendezvous room is fixed after entry");
        }
        rendezvousRoom = room;
    }
    public synchronized AgentLmpqMemberState member(int id) { return members.get(id); }
    public synchronized Collection<AgentLmpqMemberState> members() { return List.copyOf(members.values()); }
    public synchronized int memberCount() { return members.size(); }
}
