package server.agents.capabilities.partyquest.lpq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripting.event.EventInstanceManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Isolated party-level LPQ state machine. */
public final class AgentLpqSession {
    private static final Logger log = LoggerFactory.getLogger(AgentLpqSession.class);

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
    private int couponRegroupStage;
    private long couponRegroupStartedAtMs;
    private Phase mainMapRallyPhase;
    private int mainMapRallyMapId;
    private long mainMapRallyStartedAtMs;
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
    private int stage8AnnouncedAttempt = -1;
    private boolean stage8AssignmentChatEnabled;
    private final Map<Integer, Integer> stage8PlatformByMember = new LinkedHashMap<>();
    private Phase passRecoveryPhase;
    private int passRecoveryObservedCount = -1;
    private int passRecoveryObservedLiveMobs = -1;
    private int passRecoveryObservedActiveReactors = -1;
    private long passRecoveryLowestLiveMobHp = Long.MAX_VALUE;
    private long passRecoveryObservedAtMs;
    private boolean passRecoveryMobSweepAttempted;
    private boolean passRecoveryPassesAwarded;
    private Phase passHandoffRecoveryPhase;
    private long passHandoffRecoveryStartedAtMs;
    private Phase postClearTransitionPhase;
    private long postClearTransitionStartedAtMs;
    private Phase submissionReadyPhase;
    private long submissionReadyAtMs;
    private Phase loosePassPhase;
    private int loosePassObservedCount = -1;
    private long loosePassObservedAtMs;
    private boolean stage6SequenceAnnounced;
    private boolean stage2ScoutPlanAnnounced;
    private boolean stage2TrapClearAnnounced;
    private Phase assignmentsCalculatedPhase;
    private long bonusDrainedAtMs;
    private long readyAtMs;
    private long stage7CombatClearedAtMs;
    private int stage7LootSweepIndex;
    private boolean stage7ForceLootAttempted;
    private boolean rewardEligibilityFrozen;

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
        logPhaseEnd(next, nowMs);
        phase = next;
        phaseEnteredAtMs = nowMs;
        assignmentsCalculatedPhase = null;
        readyAtMs = 0L;
        resetPassRecovery();
        resetSubmissionRecovery();
        resetLoosePassRecovery();
        resetPostClearTransition();
        resetMainMapRally();
        members.values().forEach(member -> {
            member.resetForStage(member.characterId() == eventLeaderId
                    ? AgentLpqMemberState.Role.EVENT_LEADER
                    : AgentLpqMemberState.Role.GENERAL);
        });
        resetStage7LootSweep();
        rooms.reset();
        members.values().forEach(AgentLpqMemberState::clearReactorWork);
        int nextStage = next.name().startsWith("STAGE_")
                ? Integer.parseInt(next.name().substring("STAGE_".length())) : 0;
        if (nextStage != couponRegroupStage) {
            couponRegroupStage = 0;
            couponRegroupStartedAtMs = 0L;
        }
        if (next != Phase.STAGE_6) maze.reset();
        if (next != Phase.BONUS) bonusDrainedAtMs = 0L;
        markProgress(nowMs);
    }

    /**
     * Stage roles are deliberately recalculated once after every phase transition. The
     * transition itself first clears the previous stage atomically; the coordinator then
     * installs the new room/platform/combat ownership from the current party roster.
     */
    public synchronized boolean stageAssignmentsNeedRecalculation() {
        return phase.name().startsWith("STAGE_") && assignmentsCalculatedPhase != phase;
    }

    public synchronized void markStageAssignmentsRecalculated(long nowMs) {
        if (!phase.name().startsWith("STAGE_")) {
            throw new IllegalStateException("LPQ stage assignments require an active stage");
        }
        assignmentsCalculatedPhase = phase;
        markProgress(nowMs);
    }

    public synchronized void fail(String reason, long nowMs) {
        failure = reason == null ? "" : reason.trim();
        logPhaseEnd(Phase.FAILED, nowMs);
        phase = Phase.FAILED;
        phaseEnteredAtMs = nowMs;
        markProgress(nowMs);
    }

    public synchronized void complete(long nowMs) {
        logPhaseEnd(Phase.COMPLETED, nowMs);
        phase = Phase.COMPLETED;
        phaseEnteredAtMs = nowMs;
        markProgress(nowMs);
    }

    private void logPhaseEnd(Phase next, long nowMs) {
        log.info("LPQ phase timing: session={} phase={} durationMs={} next={}",
                sessionId, phase, Math.max(0L, nowMs - phaseEnteredAtMs), next);
    }
    public synchronized boolean beginTermination() { if (terminating) return false; terminating = true; return true; }
    public synchronized void markProgress(long nowMs) { lastProgressAtMs = Math.max(lastProgressAtMs, nowMs); }

    public synchronized long readyAtMs() { return readyAtMs; }

    public synchronized void setReadyAtMs(long readyAtMs) {
        this.readyAtMs = Math.max(0L, readyAtMs);
    }

    public synchronized long observePassRecovery(int partyPassCount, long nowMs) {
        if (passRecoveryPhase != phase || passRecoveryObservedCount != partyPassCount) {
            passRecoveryPhase = phase;
            passRecoveryObservedCount = partyPassCount;
            passRecoveryObservedAtMs = nowMs;
            passRecoveryMobSweepAttempted = false;
            return 0L;
        }
        return Math.max(0L, nowMs - passRecoveryObservedAtMs);
    }

    public synchronized long observePassRecoveryProgress(
            int partyPassCount, int liveMobs, int activeReactors,
            long liveMobHp, long nowMs) {
        boolean newPhase = passRecoveryPhase != phase;
        boolean passProgress = !newPhase && passRecoveryObservedCount != partyPassCount;
        boolean mobCountProgress = !newPhase
                && passRecoveryObservedLiveMobs != liveMobs;
        boolean reactorProgress = !newPhase
                && passRecoveryObservedActiveReactors != activeReactors;
        boolean damageProgress = !newPhase && !mobCountProgress && !reactorProgress
                && liveMobHp < passRecoveryLowestLiveMobHp;
        if (newPhase || passProgress || mobCountProgress || reactorProgress || damageProgress) {
            passRecoveryPhase = phase;
            passRecoveryObservedCount = partyPassCount;
            passRecoveryObservedLiveMobs = liveMobs;
            passRecoveryObservedActiveReactors = activeReactors;
            passRecoveryLowestLiveMobHp = liveMobHp;
            passRecoveryObservedAtMs = nowMs;
            passRecoveryMobSweepAttempted = false;
            return 0L;
        }
        return Math.max(0L, nowMs - passRecoveryObservedAtMs);
    }

    public synchronized boolean passRecoveryMobSweepAttempted() {
        return passRecoveryMobSweepAttempted;
    }

    public synchronized void markPassRecoveryMobSweep(long nowMs) {
        passRecoveryMobSweepAttempted = true;
        passRecoveryObservedAtMs = nowMs;
        markProgress(nowMs);
    }

    public synchronized void markPassRecoveryConsolidation(long nowMs) {
        passRecoveryObservedAtMs = nowMs;
        markProgress(nowMs);
    }

    public synchronized boolean passRecoveryPassesAwarded() {
        return passRecoveryPassesAwarded;
    }

    public synchronized void markPassRecoveryPassesAwarded(long nowMs) {
        passRecoveryPassesAwarded = true;
        passRecoveryObservedAtMs = nowMs;
        markProgress(nowMs);
    }

    public synchronized boolean passHandoffRecoveryActive() {
        return passHandoffRecoveryPhase == phase;
    }

    public synchronized boolean beginPassHandoffRecovery(long nowMs) {
        if (passHandoffRecoveryPhase == phase) return false;
        passHandoffRecoveryPhase = phase;
        passHandoffRecoveryStartedAtMs = Math.max(0L, nowMs);
        markProgress(nowMs);
        return true;
    }

    public synchronized long passHandoffRecoveryElapsed(long nowMs) {
        if (!passHandoffRecoveryActive()) return 0L;
        return Math.max(0L, nowMs - passHandoffRecoveryStartedAtMs);
    }

    private void resetPassRecovery() {
        passRecoveryPhase = null;
        passRecoveryObservedCount = -1;
        passRecoveryObservedLiveMobs = -1;
        passRecoveryObservedActiveReactors = -1;
        passRecoveryLowestLiveMobHp = Long.MAX_VALUE;
        passRecoveryObservedAtMs = 0L;
        passRecoveryMobSweepAttempted = false;
        passRecoveryPassesAwarded = false;
        passHandoffRecoveryPhase = null;
        passHandoffRecoveryStartedAtMs = 0L;
    }

    public synchronized long beginOrObservePostClearTransition(long nowMs) {
        if (postClearTransitionPhase != phase) {
            postClearTransitionPhase = phase;
            postClearTransitionStartedAtMs = Math.max(0L, nowMs);
            markProgress(nowMs);
            return 0L;
        }
        return Math.max(0L, nowMs - postClearTransitionStartedAtMs);
    }

    private void resetPostClearTransition() {
        postClearTransitionPhase = null;
        postClearTransitionStartedAtMs = 0L;
    }

    public synchronized long observeSubmissionReady(boolean ready, long nowMs) {
        if (!ready) {
            resetSubmissionRecovery();
            return 0L;
        }
        if (submissionReadyPhase != phase) {
            submissionReadyPhase = phase;
            submissionReadyAtMs = nowMs;
            return 0L;
        }
        return Math.max(0L, nowMs - submissionReadyAtMs);
    }

    private void resetSubmissionRecovery() {
        submissionReadyPhase = null;
        submissionReadyAtMs = 0L;
    }

    /** Returns how long the same set of loose passes has remained uncollected. */
    public synchronized long observeLoosePasses(int loosePassCount, long nowMs) {
        if (loosePassCount <= 0) {
            resetLoosePassRecovery();
            return 0L;
        }
        if (loosePassPhase != phase || loosePassObservedCount != loosePassCount) {
            loosePassPhase = phase;
            loosePassObservedCount = loosePassCount;
            loosePassObservedAtMs = nowMs;
            return 0L;
        }
        return Math.max(0L, nowMs - loosePassObservedAtMs);
    }

    private void resetLoosePassRecovery() {
        loosePassPhase = null;
        loosePassObservedCount = -1;
        loosePassObservedAtMs = 0L;
    }
    public synchronized boolean stage6SequenceAnnounced() { return stage6SequenceAnnounced; }
    public synchronized boolean stage6SequenceChatReady(long nowMs) {
        return !stage6SequenceAnnounced;
    }
    public synchronized void markStage6SequenceAnnounced(long nowMs) {
        stage6SequenceAnnounced = true;
        markProgress(nowMs);
    }

    public synchronized boolean stage2ScoutPlanAnnounced() { return stage2ScoutPlanAnnounced; }
    public synchronized void markStage2ScoutPlanAnnounced(long nowMs) {
        stage2ScoutPlanAnnounced = true;
        markProgress(nowMs);
    }

    public synchronized boolean stage2TrapClearAnnounced() { return stage2TrapClearAnnounced; }
    public synchronized void markStage2TrapClearAnnounced(long nowMs) {
        stage2TrapClearAnnounced = true;
        markProgress(nowMs);
    }

    /** Requires the bonus map to stay empty briefly so late reactor drops are not abandoned. */
    public synchronized long observeBonusDrained(boolean drained, long nowMs) {
        if (!drained) {
            bonusDrainedAtMs = 0L;
            return 0L;
        }
        if (bonusDrainedAtMs == 0L) {
            bonusDrainedAtMs = nowMs;
            return 0L;
        }
        return Math.max(0L, nowMs - bonusDrainedAtMs);
    }

    public synchronized long observeStage7CombatCleared(boolean cleared, long nowMs) {
        if (!cleared) {
            resetStage7LootSweep();
            return 0L;
        }
        if (stage7CombatClearedAtMs == 0L) {
            stage7CombatClearedAtMs = nowMs;
            return 0L;
        }
        return Math.max(0L, nowMs - stage7CombatClearedAtMs);
    }

    public synchronized int stage7LootSweepIndex() { return stage7LootSweepIndex; }
    public synchronized void advanceStage7LootSweep(long nowMs) {
        stage7LootSweepIndex++;
        markProgress(nowMs);
    }
    public synchronized boolean stage7ForceLootAttempted() { return stage7ForceLootAttempted; }
    public synchronized void markStage7ForceLootAttempted(long nowMs) {
        stage7ForceLootAttempted = true;
        markProgress(nowMs);
    }
    private void resetStage7LootSweep() {
        stage7CombatClearedAtMs = 0L;
        stage7LootSweepIndex = 0;
        stage7ForceLootAttempted = false;
    }
    public synchronized boolean terminal() { return phase == Phase.COMPLETED || phase == Phase.FAILED; }

    public synchronized boolean couponRegrouping(int stage) {
        return stage >= 1 && stage <= 3 && couponRegroupStage == stage;
    }

    public synchronized void beginCouponRegroup(int stage, long nowMs) {
        if (stage < 1 || stage > 3 || phase != Phase.valueOf("STAGE_" + stage)
                || couponRegroupStage == stage) return;
        couponRegroupStage = stage;
        couponRegroupStartedAtMs = nowMs;
        markProgress(nowMs);
    }

    public synchronized long couponRegroupElapsed(int stage, long nowMs) {
        if (!couponRegrouping(stage) || couponRegroupStartedAtMs <= 0L) return 0L;
        return Math.max(0L, nowMs - couponRegroupStartedAtMs);
    }

    /**
     * Starts the balloon recovery grace only after every registered participant has returned
     * to the stage's main map. A room/trap occupant resets the clock, so time spent finishing
     * an authored exit can never consume the subsequent walk-to-balloon allowance.
     */
    public synchronized long observeMainMapRally(
            int mapId, boolean everyoneOnMainMap, long nowMs) {
        if (mapId <= 0 || nowMs < 0L || !everyoneOnMainMap) {
            resetMainMapRally();
            return 0L;
        }
        if (mainMapRallyPhase != phase || mainMapRallyMapId != mapId
                || mainMapRallyStartedAtMs == 0L) {
            mainMapRallyPhase = phase;
            mainMapRallyMapId = mapId;
            mainMapRallyStartedAtMs = nowMs;
            return 0L;
        }
        return Math.max(0L, nowMs - mainMapRallyStartedAtMs);
    }

    public synchronized long mainMapRallyElapsed(int mapId, long nowMs) {
        if (mapId <= 0 || mainMapRallyPhase != phase || mainMapRallyMapId != mapId
                || mainMapRallyStartedAtMs <= 0L) return 0L;
        return Math.max(0L, nowMs - mainMapRallyStartedAtMs);
    }

    private void resetMainMapRally() {
        mainMapRallyPhase = null;
        mainMapRallyMapId = 0;
        mainMapRallyStartedAtMs = 0L;
    }

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

    public synchronized boolean stage8AssignmentAnnounced() {
        return stage8AnnouncedAttempt == stage8Attempt;
    }

    public synchronized boolean stage8AssignmentChatEnabled() {
        return stage8AssignmentChatEnabled;
    }

    public synchronized void setStage8AssignmentChatEnabled(boolean enabled) {
        if (enabled && !stage8AssignmentChatEnabled) stage8AnnouncedAttempt = -1;
        stage8AssignmentChatEnabled = enabled;
    }

    public synchronized void markStage8AssignmentAnnounced(long nowMs) {
        stage8AnnouncedAttempt = stage8Attempt;
        markProgress(nowMs);
    }

    /** Keeps shared box owners fixed while moving only members whose next formation changed. */
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
    public synchronized void freezeRewardEligibility() { rewardEligibilityFrozen = true; }
    public synchronized boolean rewardEligibilityFrozen() { return rewardEligibilityFrozen; }
    public synchronized boolean beginRewardClaim(int characterId) {
        AgentLpqMemberState member = members.get(characterId);
        return rewardEligibilityFrozen && member != null && member.beginRewardClaim();
    }
    public synchronized boolean completeRewardClaim(int characterId) {
        AgentLpqMemberState member = members.get(characterId);
        return member != null && member.completeRewardClaim();
    }
    public synchronized void cancelRewardClaim(int characterId) {
        AgentLpqMemberState member = members.get(characterId);
        if (member != null) member.cancelRewardClaim();
    }
    public synchronized void forfeitReward(int characterId) {
        AgentLpqMemberState member = members.get(characterId);
        if (member != null) member.forfeitReward();
    }
    public synchronized boolean allRewardsResolved() {
        return rewardEligibilityFrozen && members.values().stream()
                .allMatch(AgentLpqMemberState::rewardResolved);
    }
}
