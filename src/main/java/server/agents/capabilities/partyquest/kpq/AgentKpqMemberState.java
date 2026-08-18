package server.agents.capabilities.partyquest.kpq;

/** Mutable per-member state owned by one party-level KPQ session. */
public final class AgentKpqMemberState {
    public enum MemberType { AGENT, HUMAN }
    public enum Role {
        EVENT_LEADER, COUPON_COLLECTOR, PASS_DELIVERER, COMBAT_HELPER,
        PUZZLE_PARTICIPANT, STAGE5_PASS_COLLECTOR, WAITING
    }

    private final int characterId;
    private final MemberType memberType;
    private final int partyNumber;
    private Role role = Role.WAITING;
    private int couponTarget = -1;
    private boolean questionRequested;
    private boolean passCreated;
    private boolean passDelivered;
    private boolean rewardClaimed;
    private int assignedPosition;
    private long stableSinceMs;
    private long actionNotBeforeMs;
    private String blocker = "";

    public AgentKpqMemberState(int characterId, MemberType memberType, int partyNumber) {
        this.characterId = characterId;
        this.memberType = memberType;
        this.partyNumber = partyNumber;
    }

    public int characterId() { return characterId; }
    public MemberType memberType() { return memberType; }
    public int partyNumber() { return partyNumber; }
    public Role role() { return role; }
    public void setRole(Role role) { this.role = role; }
    public int couponTarget() { return couponTarget; }
    public void setCouponTarget(int couponTarget) { this.couponTarget = couponTarget; }
    public boolean questionRequested() { return questionRequested; }
    public void markQuestionRequested() { questionRequested = true; }
    public boolean passCreated() { return passCreated; }
    public void markPassCreated() { passCreated = true; }
    public boolean passDelivered() { return passDelivered; }
    public void markPassDelivered() { passDelivered = true; }
    public boolean rewardClaimed() { return rewardClaimed; }
    public void markRewardClaimed() { rewardClaimed = true; }
    public int assignedPosition() { return assignedPosition; }
    public void setAssignedPosition(int assignedPosition) {
        if (this.assignedPosition != assignedPosition) stableSinceMs = 0L;
        this.assignedPosition = assignedPosition;
    }
    public long stableSinceMs() { return stableSinceMs; }
    public void setStableSinceMs(long stableSinceMs) { this.stableSinceMs = stableSinceMs; }
    public long actionNotBeforeMs() { return actionNotBeforeMs; }
    public void setActionNotBeforeMs(long actionNotBeforeMs) { this.actionNotBeforeMs = actionNotBeforeMs; }
    public String blocker() { return blocker; }
    public void setBlocker(String blocker) { this.blocker = blocker == null ? "" : blocker; }

    public void resetForRun() {
        role = Role.WAITING;
        couponTarget = -1;
        questionRequested = false;
        passCreated = false;
        passDelivered = false;
        rewardClaimed = false;
        assignedPosition = 0;
        stableSinceMs = 0L;
        actionNotBeforeMs = 0L;
        blocker = "";
    }
}
