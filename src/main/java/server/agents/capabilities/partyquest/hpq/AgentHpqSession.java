package server.agents.capabilities.partyquest.hpq;

import scripting.event.EventInstanceManager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Isolated party-level HPQ state machine aggregate. */
public final class AgentHpqSession {
    public enum Mode { PRODUCTION, BACKGROUND_POPULATION, TEST_OBSERVATION }
    public enum PartyOwnership { EXTERNAL, HPQ_OWNED }
    public enum BonusMode { SKIP, ENTER, HUMAN_CHOICE }
    public enum Phase {
        PREPARING, ENTERING, COLLECTING_SEEDS, PLANTING_SEEDS, DEFENDING_BUNNY,
        DELIVERING_CAKES, BONUS_DECISION, BONUS_FARMING, CLAIMING_REWARD,
        EXITING, COMPLETED, FAILED
    }

    private final String sessionId = UUID.randomUUID().toString();
    private final Mode mode;
    private final long seed;
    private final int operatorId;
    private final int requestedPartySize;
    private final long startedAtMs;
    private final Map<Integer, AgentHpqMemberState> members = new LinkedHashMap<>();
    private Phase phase = Phase.PREPARING;
    private int eventLeaderId;
    private int executionAgentId;
    private long executionLeaseUntilMs;
    private long lastProgressAtMs;
    private long phaseEnteredAtMs;
    private long readyAtMs;
    private boolean defenseHostilesPresent;
    private int defenseWaveOrdinal;
    private boolean paused;
    private boolean terminating;
    private String failure = "";
    private EventInstanceManager eventInstance;
    private PartyOwnership partyOwnership = PartyOwnership.EXTERNAL;
    private BonusMode bonusMode = BonusMode.SKIP;
    private boolean rewardEligibilityFrozen;

    public AgentHpqSession(Mode mode, long seed, int operatorId, int requestedPartySize, long nowMs) {
        if (mode == null || operatorId <= 0 || requestedPartySize < 3 || requestedPartySize > 6
                || nowMs < 0L) {
            throw new IllegalArgumentException("valid HPQ session values are required");
        }
        this.mode = mode;
        this.seed = seed;
        this.operatorId = operatorId;
        this.requestedPartySize = requestedPartySize;
        this.startedAtMs = nowMs;
        this.lastProgressAtMs = nowMs;
        this.phaseEnteredAtMs = nowMs;
    }

    public synchronized void addMember(int characterId, AgentHpqMemberState.MemberType type) {
        if (members.size() >= requestedPartySize && !members.containsKey(characterId)) {
            throw new IllegalStateException("HPQ session is full");
        }
        members.computeIfAbsent(characterId, id -> new AgentHpqMemberState(id, type));
    }

    public synchronized void setLeadership(int eventLeaderId, int executionAgentId) {
        if (!members.containsKey(eventLeaderId) || !members.containsKey(executionAgentId)
                || members.get(executionAgentId).memberType() != AgentHpqMemberState.MemberType.AGENT) {
            throw new IllegalArgumentException("HPQ leaders must be registered members and execution must be Agent-owned");
        }
        this.eventLeaderId = eventLeaderId;
        this.executionAgentId = executionAgentId;
        members.get(eventLeaderId).assign(AgentHpqMemberState.Role.EVENT_LEADER, 0);
    }

    public synchronized boolean claimExecutionTick(int characterId, long nowMs, long leaseMs) {
        AgentHpqMemberState member = members.get(characterId);
        if (member == null || member.memberType() != AgentHpqMemberState.MemberType.AGENT) return false;
        if (executionAgentId != characterId && nowMs < executionLeaseUntilMs) return false;
        executionAgentId = characterId;
        executionLeaseUntilMs = nowMs + Math.max(250L, leaseMs);
        return true;
    }

    public synchronized boolean claimExpiredExecutionTick(int characterId, long nowMs, long leaseMs) {
        AgentHpqMemberState member = members.get(characterId);
        if (member == null || member.memberType() != AgentHpqMemberState.MemberType.AGENT
                || nowMs < executionLeaseUntilMs) return false;
        executionAgentId = characterId;
        executionLeaseUntilMs = nowMs + Math.max(250L, leaseMs);
        return true;
    }

    public synchronized void transition(Phase phase, long nowMs) {
        if (phase == null || terminal()) return;
        this.phase = phase;
        this.phaseEnteredAtMs = nowMs;
        markProgress(nowMs);
    }

    public synchronized void fail(String reason, long nowMs) {
        failure = reason == null ? "" : reason.trim();
        phase = Phase.FAILED;
        markProgress(nowMs);
    }

    public synchronized void complete(long nowMs) {
        phase = Phase.COMPLETED;
        markProgress(nowMs);
    }

    public synchronized boolean beginTermination() {
        if (terminating) return false;
        terminating = true;
        return true;
    }

    public synchronized void markProgress(long nowMs) {
        lastProgressAtMs = Math.max(lastProgressAtMs, nowMs);
    }

    public synchronized boolean terminal() {
        return phase == Phase.COMPLETED || phase == Phase.FAILED;
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
    public synchronized long readyAtMs() { return readyAtMs; }
    public synchronized void setReadyAtMs(long readyAtMs) {
        this.readyAtMs = Math.max(0L, readyAtMs);
    }
    public synchronized boolean observeDefenseHostiles(boolean present) {
        boolean newWave = present && !defenseHostilesPresent;
        defenseHostilesPresent = present;
        if (newWave) defenseWaveOrdinal++;
        return newWave;
    }
    public synchronized int defenseWaveOrdinal() { return defenseWaveOrdinal; }
    public synchronized boolean paused() { return paused; }
    public synchronized void setPaused(boolean paused) { this.paused = paused; }
    public synchronized String failure() { return failure; }
    public synchronized EventInstanceManager eventInstance() { return eventInstance; }
    public synchronized void bindEventInstance(EventInstanceManager eventInstance) {
        this.eventInstance = eventInstance;
    }
    public synchronized void clearEventInstance() { eventInstance = null; }
    public synchronized PartyOwnership partyOwnership() { return partyOwnership; }
    public synchronized void setPartyOwnership(PartyOwnership partyOwnership) {
        if (partyOwnership == null) throw new IllegalArgumentException("HPQ party ownership is required");
        this.partyOwnership = partyOwnership;
    }
    public synchronized BonusMode bonusMode() { return bonusMode; }
    public synchronized void setBonusMode(BonusMode bonusMode) {
        if (bonusMode == null) throw new IllegalArgumentException("HPQ bonus mode is required");
        this.bonusMode = bonusMode;
    }
    public synchronized AgentHpqMemberState member(int characterId) { return members.get(characterId); }
    public synchronized Collection<AgentHpqMemberState> members() { return java.util.List.copyOf(members.values()); }
    public synchronized int memberCount() { return members.size(); }

    public synchronized void freezeRewardEligibility() { rewardEligibilityFrozen = true; }
    public synchronized boolean rewardEligibilityFrozen() { return rewardEligibilityFrozen; }
    public synchronized boolean beginRewardClaim(int characterId) {
        AgentHpqMemberState member = members.get(characterId);
        return rewardEligibilityFrozen && member != null && member.beginRewardClaim();
    }
    public synchronized boolean completeRewardClaim(int characterId) {
        AgentHpqMemberState member = members.get(characterId);
        return member != null && member.completeRewardClaim();
    }
    public synchronized void cancelRewardClaim(int characterId) {
        AgentHpqMemberState member = members.get(characterId);
        if (member != null) member.cancelRewardClaim();
    }
    public synchronized void forfeitReward(int characterId) {
        AgentHpqMemberState member = members.get(characterId);
        if (member != null) member.forfeitReward();
    }
    public synchronized boolean allRewardsResolved() {
        return rewardEligibilityFrozen && members.values().stream()
                .allMatch(AgentHpqMemberState::rewardResolved);
    }
}
