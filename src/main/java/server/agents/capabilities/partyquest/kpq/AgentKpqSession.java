package server.agents.capabilities.partyquest.kpq;

import scripting.event.EventInstanceManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Party-level KPQ state. All mutation is synchronized through this aggregate. */
public final class AgentKpqSession {
    public enum Mode { PRODUCTION, BACKGROUND_POPULATION, TEST_OBSERVATION }
    public enum PartyOwnership { EXTERNAL, KPQ_OWNED }
    public enum Phase {
        PREPARING, ENTERING, STAGE_1, STAGE_2, STAGE_3, STAGE_4,
        STAGE_5, CLAIMING_REWARDS, EXITING, COMPLETED, FAILED
    }

    private final String sessionId;
    private final Mode mode;
    private final long seed;
    private final int operatorId;
    private final int requestedPartySize;
    private PartyOwnership partyOwnership = PartyOwnership.EXTERNAL;
    private final Map<Integer, AgentKpqMemberState> members = new LinkedHashMap<>();
    private Phase phase = Phase.PREPARING;
    private int eventLeaderId;
    private int coordinatorAgentId;
    private int formationCallerId;
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
    private final LinkedHashSet<String> narrationKeys = new LinkedHashSet<>();
    private int squishyShoesWinnerId;
    private int requestedCheckpointStage = 1;
    private int puzzleValidationRevision;
    private int consumedPuzzleValidationRevision;
    private int puzzleValidationStage;
    private boolean puzzleValidationAccepted;
    private long couponSweepStartedAtMs;
    private long nextCouponSweepAtMs;
    private int couponSweepCollectorId;
    private long missingPassSinceMs;
    private long puzzleCheckAtMs;
    private long squishyShoesSeenAtMs;
    private boolean squishyShoesResolved;
    private String blockerKey = "";
    private long blockerSinceMs;
    private int blockerAttempts;
    private boolean terminationStarted;
    private EventInstanceManager eventInstance;
    private int stage5LastMobCount = -1;
    private int stage5LastPassCount = -1;
    private long stage5BossDefeatedAtMs;

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

    public synchronized void setLeadership(int eventLeaderId, int coordinatorAgentId) {
        AgentKpqMemberState eventLeader = members.get(eventLeaderId);
        AgentKpqMemberState coordinator = members.get(coordinatorAgentId);
        if (eventLeader == null || coordinator == null
                || coordinator.memberType() != AgentKpqMemberState.MemberType.AGENT) {
            throw new IllegalArgumentException("KPQ leadership requires a member leader and an Agent coordinator");
        }
        members.values().stream()
                .filter(member -> member.role() == AgentKpqMemberState.Role.EVENT_LEADER)
                .forEach(member -> member.setRole(AgentKpqMemberState.Role.WAITING));
        this.eventLeaderId = eventLeaderId;
        this.coordinatorAgentId = coordinatorAgentId;
        this.formationCallerId = coordinatorAgentId;
        eventLeader.setRole(AgentKpqMemberState.Role.EVENT_LEADER);
    }

    private int nextPartyNumber() {
        for (int number = 1; number <= 6; number++) {
            int candidate = number;
            if (members.values().stream().noneMatch(member -> member.partyNumber() == candidate)) return number;
        }
        throw new IllegalStateException("No KPQ party number is available");
    }

    public synchronized boolean claimCoordinatorTick(int characterId, long nowMs) {
        return claimCoordinatorTick(characterId, nowMs, 3_000L);
    }

    public synchronized boolean claimCoordinatorTick(int characterId, long nowMs, long leaseMs) {
        AgentKpqMemberState candidate = members.get(characterId);
        if (candidate == null || candidate.memberType() != AgentKpqMemberState.MemberType.AGENT
                || nowMs == lastCoordinatorTickMs) {
            return false;
        }
        boolean current = characterId == coordinatorAgentId;
        boolean expired = lastCoordinatorTickMs == Long.MIN_VALUE
                || nowMs - lastCoordinatorTickMs >= Math.max(1L, leaseMs);
        if (!current && !expired) return false;
        if (!current) {
            coordinatorAgentId = characterId;
            formationCallerId = characterId;
        }
        lastCoordinatorTickMs = nowMs;
        return true;
    }

    public synchronized boolean claimExpiredCoordinatorTick(int characterId, long nowMs, long leaseMs) {
        AgentKpqMemberState candidate = members.get(characterId);
        if (candidate == null || candidate.memberType() != AgentKpqMemberState.MemberType.AGENT
                || (lastCoordinatorTickMs != Long.MIN_VALUE
                && nowMs - lastCoordinatorTickMs < Math.max(1L, leaseMs))) {
            return false;
        }
        coordinatorAgentId = characterId;
        formationCallerId = characterId;
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
        puzzleValidationRevision = 0;
        consumedPuzzleValidationRevision = 0;
        puzzleValidationStage = 0;
        puzzleValidationAccepted = false;
        couponSweepStartedAtMs = 0L;
        nextCouponSweepAtMs = 0L;
        couponSweepCollectorId = 0;
        missingPassSinceMs = 0L;
        puzzleCheckAtMs = 0L;
        blockerKey = "";
        blockerSinceMs = 0L;
        blockerAttempts = 0;
        stage5LastMobCount = -1;
        stage5LastPassCount = -1;
        stage5BossDefeatedAtMs = 0L;
        members.values().forEach(member -> {
            member.setAssignedPosition(0);
            member.setStableSinceMs(0L);
            member.setActionNotBeforeMs(0L);
            member.setFidgetedAttemptId(-1);
            member.clearFidget();
            member.clearBlocker();
        });
    }

    public synchronized void fail(String reason, long nowMs) {
        failure = reason == null ? "unknown KPQ failure" : reason;
        transition(Phase.FAILED, nowMs);
    }

    public synchronized void complete(long nowMs) {
        transition(Phase.COMPLETED, nowMs);
    }

    public synchronized boolean beginTermination() {
        if (terminationStarted) return false;
        terminationStarted = true;
        return true;
    }

    public synchronized int observeBlocker(String key, long nowMs) {
        String next = key == null ? "unknown" : key;
        if (!next.equals(blockerKey)) {
            blockerKey = next;
            blockerSinceMs = nowMs;
            blockerAttempts = 1;
        } else {
            blockerAttempts++;
        }
        return blockerAttempts;
    }

    public synchronized void clearBlocker() {
        blockerKey = "";
        blockerSinceMs = 0L;
        blockerAttempts = 0;
    }

    public synchronized boolean narrateOnce(String key) {
        return key != null && narrationKeys.add(key);
    }

    public synchronized void recordHumanPuzzleValidation(int stage, boolean accepted) {
        puzzleValidationStage = stage;
        puzzleValidationAccepted = accepted;
        puzzleValidationRevision++;
    }

    public synchronized PuzzleValidation consumeHumanPuzzleValidation(int stage) {
        if (puzzleValidationRevision <= consumedPuzzleValidationRevision
                || puzzleValidationStage != stage) {
            return null;
        }
        consumedPuzzleValidationRevision = puzzleValidationRevision;
        return new PuzzleValidation(puzzleValidationRevision, puzzleValidationAccepted);
    }

    public synchronized String sessionId() { return sessionId; }
    public synchronized Mode mode() { return mode; }
    public synchronized PartyOwnership partyOwnership() { return partyOwnership; }
    public synchronized void setPartyOwnership(PartyOwnership ownership) {
        partyOwnership = ownership == null ? PartyOwnership.EXTERNAL : ownership;
    }
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
    public synchronized long couponSweepStartedAtMs() { return couponSweepStartedAtMs; }
    public synchronized void setCouponSweepStartedAtMs(long value) {
        couponSweepStartedAtMs = Math.max(0L, value);
    }
    public synchronized long nextCouponSweepAtMs() { return nextCouponSweepAtMs; }
    public synchronized void setNextCouponSweepAtMs(long value) {
        nextCouponSweepAtMs = Math.max(0L, value);
    }
    public synchronized int couponSweepCollectorId() { return couponSweepCollectorId; }
    public synchronized void setCouponSweepCollectorId(int value) {
        couponSweepCollectorId = Math.max(0, value);
    }
    public synchronized long missingPassSinceMs() { return missingPassSinceMs; }
    public synchronized void setMissingPassSinceMs(long value) {
        missingPassSinceMs = Math.max(0L, value);
    }
    public synchronized long puzzleCheckAtMs() { return puzzleCheckAtMs; }
    public synchronized void setPuzzleCheckAtMs(long value) { puzzleCheckAtMs = Math.max(0L, value); }
    public synchronized long squishyShoesSeenAtMs() { return squishyShoesSeenAtMs; }
    public synchronized void setSquishyShoesSeenAtMs(long value) {
        squishyShoesSeenAtMs = Math.max(0L, value);
    }
    public synchronized boolean squishyShoesResolved() { return squishyShoesResolved; }
    public synchronized void markSquishyShoesResolved() { squishyShoesResolved = true; }
    public synchronized String blockerKey() { return blockerKey; }
    public synchronized long blockerSinceMs() { return blockerSinceMs; }
    public synchronized int blockerAttempts() { return blockerAttempts; }
    public synchronized boolean terminationStarted() { return terminationStarted; }
    public synchronized EventInstanceManager eventInstance() { return eventInstance; }
    public synchronized void bindEventInstance(EventInstanceManager event) { eventInstance = event; }
    public synchronized void clearEventInstance() { eventInstance = null; }
    public synchronized boolean observeStage5Progress(int mobCount, int passCount) {
        boolean changed = stage5LastMobCount < 0 || mobCount < stage5LastMobCount
                || passCount > stage5LastPassCount;
        stage5LastMobCount = mobCount;
        stage5LastPassCount = passCount;
        return changed;
    }

    public synchronized boolean stage5ReviveGraceActive(
            int bossCount, long nowMs, long graceMs) {
        if (bossCount > 0) {
            stage5BossDefeatedAtMs = 0L;
            return false;
        }
        if (stage5BossDefeatedAtMs == 0L) {
            stage5BossDefeatedAtMs = nowMs;
        }
        return nowMs - stage5BossDefeatedAtMs < Math.max(0L, graceMs);
    }

    public record PuzzleValidation(int revision, boolean accepted) {
    }
}
