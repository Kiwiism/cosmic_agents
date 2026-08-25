package server.agents.capabilities.partyquest.lpq;

import scripting.event.EventInstanceManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Isolated party-level LPQ state machine. */
public final class AgentLpqSession {
    public enum Mode { PRODUCTION, BACKGROUND_POPULATION, TEST_OBSERVATION }
    public enum PartyOwnership { EXTERNAL, LPQ_OWNED }
    public enum BonusMode { SKIP, ENTER, HUMAN_CHOICE }
    public enum Phase {
        PREPARING, ENTERING,
        STAGE_1, STAGE_2, STAGE_3, STAGE_4, STAGE_5,
        STAGE_6, STAGE_7, STAGE_8, STAGE_9,
        BONUS, CLAIMING_REWARD, EXITING, COMPLETED, FAILED
    }

    private final String sessionId = UUID.randomUUID().toString();
    private final Mode mode;
    private final long seed;
    private final int operatorId;
    private final int requestedPartySize;
    private final long startedAtMs;
    private final Map<Integer, AgentLpqMemberState> members = new LinkedHashMap<>();
    private final AgentLpqRoomAssignment rooms = new AgentLpqRoomAssignment();
    private final AgentLpqPortalMazeState maze = new AgentLpqPortalMazeState();
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
    private PartyOwnership partyOwnership = PartyOwnership.EXTERNAL;
    private BonusMode bonusMode = BonusMode.ENTER;
    private List<List<Integer>> stage8Order = List.of();
    private int stage8Attempt;
    private final Map<Integer, Integer> stage8PlatformByMember = new LinkedHashMap<>();

    public AgentLpqSession(Mode mode, long seed, int operatorId, int requestedPartySize, long nowMs) {
        if (mode == null || operatorId <= 0
                || requestedPartySize < AgentLpqDefinition.MIN_PARTY_SIZE
                || requestedPartySize > AgentLpqDefinition.MAX_PARTY_SIZE || nowMs < 0L) {
            throw new IllegalArgumentException("valid LPQ session values are required");
        }
        this.mode = mode;
        this.seed = seed;
        this.operatorId = operatorId;
        this.requestedPartySize = requestedPartySize;
        this.startedAtMs = nowMs;
        this.lastProgressAtMs = nowMs;
        this.phaseEnteredAtMs = nowMs;
    }

    public synchronized void addMember(int characterId, AgentLpqMemberState.MemberType type) {
        if (members.size() >= requestedPartySize && !members.containsKey(characterId)) {
            throw new IllegalStateException("LPQ session is full");
        }
        members.computeIfAbsent(characterId, id -> new AgentLpqMemberState(id, type));
    }

    public synchronized void setLeadership(int eventLeaderId, int executionAgentId) {
        AgentLpqMemberState execution = members.get(executionAgentId);
        if (!members.containsKey(eventLeaderId) || execution == null
                || execution.memberType() != AgentLpqMemberState.MemberType.AGENT) {
            throw new IllegalArgumentException("LPQ leaders must be members and execution must be Agent-owned");
        }
        this.eventLeaderId = eventLeaderId;
        this.executionAgentId = executionAgentId;
        members.get(eventLeaderId).assign(AgentLpqMemberState.Role.EVENT_LEADER, 0);
    }

    public synchronized boolean claimExecutionTick(int characterId, long nowMs, long leaseMs) {
        AgentLpqMemberState member = members.get(characterId);
        if (member == null || member.memberType() != AgentLpqMemberState.MemberType.AGENT) return false;
        if (executionAgentId != characterId && nowMs < executionLeaseUntilMs) return false;
        executionAgentId = characterId;
        executionLeaseUntilMs = nowMs + Math.max(250L, leaseMs);
        return true;
    }

    public synchronized boolean claimExpiredExecutionTick(int characterId, long nowMs, long leaseMs) {
        AgentLpqMemberState member = members.get(characterId);
        if (member == null || member.memberType() != AgentLpqMemberState.MemberType.AGENT
                || nowMs < executionLeaseUntilMs) return false;
        executionAgentId = characterId;
        executionLeaseUntilMs = nowMs + Math.max(250L, leaseMs);
        return true;
    }

    public synchronized void transition(Phase next, long nowMs) {
        if (next == null || terminal() || next == phase) return;
        phase = next;
        phaseEnteredAtMs = nowMs;
        rooms.reset();
        if (next != Phase.STAGE_6) maze.reset();
        markProgress(nowMs);
    }

    public synchronized void fail(String reason, long nowMs) {
        failure = reason == null ? "" : reason.trim();
        phase = Phase.FAILED;
        markProgress(nowMs);
    }

    public synchronized void complete(long nowMs) { phase = Phase.COMPLETED; markProgress(nowMs); }
    public synchronized boolean beginTermination() { if (terminating) return false; terminating = true; return true; }
    public synchronized void markProgress(long nowMs) { lastProgressAtMs = Math.max(lastProgressAtMs, nowMs); }
    public synchronized boolean terminal() { return phase == Phase.COMPLETED || phase == Phase.FAILED; }

    public synchronized void initializeStage8Order() {
        if (stage8Order.isEmpty()) stage8Order = AgentLpqCombinationOrder.fiveOfNine();
    }

    public synchronized List<Integer> stage8Combination() {
        initializeStage8Order();
        return stage8Order.get(Math.min(stage8Attempt, stage8Order.size() - 1));
    }

    public synchronized void advanceStage8(long nowMs) {
        initializeStage8Order();
        if (stage8Attempt + 1 < stage8Order.size()) stage8Attempt++;
        markProgress(nowMs);
    }

    /** Keeps every shared box owner fixed between Gray-order guesses; exactly one member moves. */
    public synchronized Map<Integer, Integer> stage8Assignments(List<Integer> participantIds) {
        if (participantIds == null || participantIds.size() < 5) {
            throw new IllegalArgumentException("five LPQ Stage 8 participants are required");
        }
        List<Integer> participants = participantIds.stream().limit(5).toList();
        List<Integer> target = stage8Combination();
        stage8PlatformByMember.keySet().removeIf(id -> !participants.contains(id));
        if (stage8PlatformByMember.isEmpty()) {
            for (int index = 0; index < 5; index++) {
                stage8PlatformByMember.put(participants.get(index), target.get(index));
            }
            return Map.copyOf(stage8PlatformByMember);
        }
        stage8PlatformByMember.entrySet().removeIf(entry -> !target.contains(entry.getValue()));
        Set<Integer> used = new java.util.LinkedHashSet<>(stage8PlatformByMember.values());
        List<Integer> freeMembers = participants.stream()
                .filter(id -> !stage8PlatformByMember.containsKey(id)).toList();
        List<Integer> freePlatforms = target.stream().filter(platform -> !used.contains(platform)).toList();
        for (int index = 0; index < Math.min(freeMembers.size(), freePlatforms.size()); index++) {
            stage8PlatformByMember.put(freeMembers.get(index), freePlatforms.get(index));
        }
        return Map.copyOf(stage8PlatformByMember);
    }

    public String sessionId() { return sessionId; }
    public Mode mode() { return mode; }
    public long seed() { return seed; }
    public int operatorId() { return operatorId; }
    public int requestedPartySize() { return requestedPartySize; }
    public long startedAtMs() { return startedAtMs; }
    public synchronized Phase phase() { return phase; }
    public synchronized int eventLeaderId() { return eventLeaderId; }
    public synchronized int executionAgentId() { return executionAgentId; }
    public synchronized long lastProgressAtMs() { return lastProgressAtMs; }
    public synchronized long phaseEnteredAtMs() { return phaseEnteredAtMs; }
    public synchronized boolean paused() { return paused; }
    public synchronized void setPaused(boolean paused) { this.paused = paused; }
    public synchronized String failure() { return failure; }
    public synchronized EventInstanceManager eventInstance() { return eventInstance; }
    public synchronized void bindEventInstance(EventInstanceManager eventInstance) { this.eventInstance = eventInstance; }
    public synchronized void clearEventInstance() { eventInstance = null; }
    public synchronized PartyOwnership partyOwnership() { return partyOwnership; }
    public synchronized void setPartyOwnership(PartyOwnership value) { partyOwnership = value; }
    public synchronized BonusMode bonusMode() { return bonusMode; }
    public synchronized void setBonusMode(BonusMode value) { bonusMode = value; }
    public AgentLpqRoomAssignment rooms() { return rooms; }
    public AgentLpqPortalMazeState maze() { return maze; }
    public synchronized int stage8Attempt() { return stage8Attempt; }
    public synchronized AgentLpqMemberState member(int id) { return members.get(id); }
    public synchronized Collection<AgentLpqMemberState> members() { return List.copyOf(members.values()); }
    public synchronized int memberCount() { return members.size(); }
}
