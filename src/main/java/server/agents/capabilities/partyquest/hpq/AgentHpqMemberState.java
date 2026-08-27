package server.agents.capabilities.partyquest.hpq;

/** Per-participant HPQ state. It is never stored in another PQ's aggregate. */
public final class AgentHpqMemberState {
    public enum MemberType { AGENT, HUMAN }
    public enum Role { EVENT_LEADER, SEED_COLLECTOR, SEED_PLANTER, BUNNY_GUARD, CAKE_COLLECTOR }
    public enum RewardState { PENDING, CLAIMING, CLAIMED, FORFEITED }

    private final int characterId;
    private final MemberType memberType;
    private Role role;
    private int assignedSeedItemId;
    private long nextActionAtMs;
    private RewardState rewardState = RewardState.PENDING;

    public AgentHpqMemberState(int characterId, MemberType memberType) {
        if (characterId <= 0 || memberType == null) {
            throw new IllegalArgumentException("valid HPQ member values are required");
        }
        this.characterId = characterId;
        this.memberType = memberType;
        this.role = Role.SEED_COLLECTOR;
    }

    public int characterId() { return characterId; }
    public MemberType memberType() { return memberType; }
    public Role role() { return role; }
    public int assignedSeedItemId() { return assignedSeedItemId; }
    public long nextActionAtMs() { return nextActionAtMs; }
    public RewardState rewardState() { return rewardState; }
    public boolean rewardClaimed() { return rewardState == RewardState.CLAIMED; }
    public boolean rewardResolved() {
        return rewardState == RewardState.CLAIMED || rewardState == RewardState.FORFEITED;
    }

    boolean beginRewardClaim() {
        if (rewardState != RewardState.PENDING) return false;
        rewardState = RewardState.CLAIMING;
        return true;
    }

    boolean completeRewardClaim() {
        if (rewardState != RewardState.CLAIMING) return false;
        rewardState = RewardState.CLAIMED;
        return true;
    }

    void cancelRewardClaim() {
        if (rewardState == RewardState.CLAIMING) rewardState = RewardState.PENDING;
    }

    void forfeitReward() {
        if (rewardState == RewardState.PENDING) rewardState = RewardState.FORFEITED;
    }

    public void assign(Role role, int assignedSeedItemId) {
        if (role == null || (assignedSeedItemId != 0 && !AgentHpqDefinition.isSeed(assignedSeedItemId))) {
            throw new IllegalArgumentException("valid HPQ role assignment is required");
        }
        this.role = role;
        this.assignedSeedItemId = assignedSeedItemId;
    }

    public void deferUntil(long nextActionAtMs) {
        this.nextActionAtMs = Math.max(0L, nextActionAtMs);
    }
}
