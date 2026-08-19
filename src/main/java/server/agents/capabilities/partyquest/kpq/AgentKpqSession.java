package server.agents.capabilities.partyquest.kpq;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Party-level KPQ state. All mutation is synchronized through this aggregate. */
public final class AgentKpqSession {
    public enum Mode { PRODUCTION, TEST_OBSERVATION }
    public enum Phase {
        RECRUITING, PREPARING, ENTERING, STAGE_1, STAGE_2, STAGE_3, STAGE_4,
        STAGE_5, CLAIMING_REWARDS, EXITING, WAITING_OUTSIDE_TEST, COMPLETED, FAILED
    }

    private final String sessionId;
    private final Mode mode;
    private final long seed;
    private final int operatorId;
    private final int requestedPartySize;
    private final Map<Integer, AgentKpqMemberState> members = new LinkedHashMap<>();
    private Phase phase = Phase.RECRUITING;
    private int eventLeaderId;
    private int coordinatorAgentId;
    private int formationCallerId;
    private final List<String> preparationWarnings = new ArrayList<>();
    private int stageStep;
    private int attemptIndex;
    private int attemptId;
    private List<Integer> combination = List.of();
    private long phaseEnteredAtMs;
    private long lastProgressAtMs;
    private long readyAtMs;
    private long lastCoordinatorTickMs = Long.MIN_VALUE;
    private boolean paused;
    private String failure = "";
    private String lastNarrationKey = "";
    private int squishyShoesWinnerId;
    private int requestedCheckpointStage = 1;

    public AgentKpqSession(Mode mode, long seed, int operatorId, int requestedPartySize, long nowMs) {
        if (requestedPartySize < 3 || requestedPartySize > 4) {
            throw new IllegalArgumentException("KPQ party size must be 3 or 4");
        }
        this.sessionId = "kpq-" + UUID.randomUUID();
        this.mode = mode;
        this.seed = seed;
        this.operatorId = operatorId;
        this.requestedPartySize = requestedPartySize;
        this.phaseEnteredAtMs = nowMs;
        this.lastProgressAtMs = nowMs;
    }

    public synchronized void addMember(int characterId, AgentKpqMemberState.MemberType type) {
        members.putIfAbsent(characterId, new AgentKpqMemberState(characterId, type, nextPartyNumber()));
        if (eventLeaderId == 0) {
            eventLeaderId = characterId;
            coordinatorAgentId = characterId;
            formationCallerId = characterId;
            members.get(characterId).setRole(AgentKpqMemberState.Role.EVENT_LEADER);
        }
    }

    public synchronized void removeMember(int characterId) {
        members.remove(characterId);
    }

    private int nextPartyNumber() {
        for (int number = 1; number <= 6; number++) {
            int candidate = number;
            if (members.values().stream().noneMatch(member -> member.partyNumber() == candidate)) return number;
        }
        throw new IllegalStateException("No KPQ party number is available");
    }

    public synchronized boolean claimCoordinatorTick(int characterId, long nowMs) {
        if (characterId != coordinatorAgentId || nowMs == lastCoordinatorTickMs) return false;
        lastCoordinatorTickMs = nowMs;
        return true;
    }

    public synchronized void transition(Phase next, long nowMs) {
        phase = next;
        phaseEnteredAtMs = nowMs;
        lastProgressAtMs = nowMs;
        stageStep = 0;
        attemptIndex = 0;
        attemptId = 0;
        combination = List.of();
        members.values().forEach(member -> {
            member.setAssignedPosition(0);
            member.setStableSinceMs(0L);
            member.setActionNotBeforeMs(0L);
        });
    }

    public synchronized void resetForRun(long nowMs) {
        members.values().forEach(AgentKpqMemberState::resetForRun);
        AgentKpqMemberState leader = members.get(eventLeaderId);
        if (leader != null) leader.setRole(AgentKpqMemberState.Role.EVENT_LEADER);
        failure = "";
        preparationWarnings.clear();
        paused = false;
        transition(Phase.PREPARING, nowMs);
    }

    public synchronized void addPreparationWarning(String warning) {
        if (warning != null && !warning.isBlank()) {
            preparationWarnings.add(warning);
        }
    }

    public synchronized List<String> preparationWarnings() {
        return new ArrayList<>(preparationWarnings);
    }

    public synchronized void fail(String reason, long nowMs) {
        failure = reason == null ? "unknown KPQ failure" : reason;
        transition(Phase.FAILED, nowMs);
    }

    public synchronized boolean narrateOnce(String key) {
        if (key == null || key.equals(lastNarrationKey)) return false;
        lastNarrationKey = key;
        return true;
    }

    public synchronized String sessionId() { return sessionId; }
    public synchronized Mode mode() { return mode; }
    public synchronized long seed() { return seed; }
    public synchronized int operatorId() { return operatorId; }
    public synchronized int requestedPartySize() { return requestedPartySize; }
    public synchronized Phase phase() { return phase; }
    public synchronized int eventLeaderId() { return eventLeaderId; }
    public synchronized int coordinatorAgentId() { return coordinatorAgentId; }
    public synchronized void setCoordinatorAgentId(int id) { coordinatorAgentId = id; }
    public synchronized int formationCallerId() { return formationCallerId; }
    public synchronized void setFormationCallerId(int id) { formationCallerId = id; }
    public synchronized List<AgentKpqMemberState> members() { return new ArrayList<>(members.values()); }
    public synchronized AgentKpqMemberState member(int id) { return members.get(id); }
    public synchronized int memberCount() { return members.size(); }
    public synchronized int stageStep() { return stageStep; }
    public synchronized void setStageStep(int stageStep) { this.stageStep = stageStep; }
    public synchronized int attemptIndex() { return attemptIndex; }
    public synchronized void setAttemptIndex(int attemptIndex) { this.attemptIndex = attemptIndex; }
    public synchronized int nextAttemptId() { return ++attemptId; }
    public synchronized int attemptId() { return attemptId; }
    public synchronized List<Integer> combination() { return combination; }
    public synchronized void setCombination(List<Integer> combination) { this.combination = List.copyOf(combination); }
    public synchronized long phaseEnteredAtMs() { return phaseEnteredAtMs; }
    public synchronized long lastProgressAtMs() { return lastProgressAtMs; }
    public synchronized void markProgress(long nowMs) { lastProgressAtMs = nowMs; }
    public synchronized long readyAtMs() { return readyAtMs; }
    public synchronized void setReadyAtMs(long readyAtMs) { this.readyAtMs = readyAtMs; }
    public synchronized boolean paused() { return paused; }
    public synchronized void setPaused(boolean paused) { this.paused = paused; }
    public synchronized String failure() { return failure; }
    public synchronized int squishyShoesWinnerId() { return squishyShoesWinnerId; }
    public synchronized void setSquishyShoesWinnerId(int id) { squishyShoesWinnerId = id; }
    public synchronized int requestedCheckpointStage() { return requestedCheckpointStage; }
    public synchronized void setRequestedCheckpointStage(int stage) {
        if (stage < 1 || stage > 5) throw new IllegalArgumentException("KPQ checkpoint must be 1-5");
        requestedCheckpointStage = stage;
    }
}
