package server.agents.capabilities.partyquest.kpq;

import java.awt.Point;

/** Mutable per-member state owned by one party-level KPQ session. */
public final class AgentKpqMemberState {
    public enum MemberType { AGENT, HUMAN }
    public enum Role {
        EVENT_LEADER, COUPON_COLLECTOR, PASS_DELIVERER, COMBAT_HELPER,
        PUZZLE_PARTICIPANT, STAGE5_PASS_COLLECTOR, SQUISHY_SHOES_COLLECTOR, WAITING
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
    private int couponMilestone = 0;
    private int reportedPassCount;
    private int fidgetedAttemptId = -1;
    private Point fidgetTarget;
    private long fidgetUntilMs;
    private String blocker = "";
    private long blockerSinceMs;
    private int blockerAttempts;
    private long nextRetryAtMs;
    private int enteredStage;
    private long stageMovementNotBeforeMs;

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
    public int couponMilestone() { return couponMilestone; }
    public void setCouponMilestone(int couponMilestone) { this.couponMilestone = Math.max(0, Math.min(100, couponMilestone)); }
    public int reportedPassCount() { return reportedPassCount; }
    public void setReportedPassCount(int reportedPassCount) { this.reportedPassCount = Math.max(0, reportedPassCount); }
    public int fidgetedAttemptId() { return fidgetedAttemptId; }
    public void setFidgetedAttemptId(int attemptId) { fidgetedAttemptId = attemptId; }
    public Point fidgetTarget() { return fidgetTarget == null ? null : new Point(fidgetTarget); }
    public long fidgetUntilMs() { return fidgetUntilMs; }
    public void beginFidget(Point target, long untilMs) {
        fidgetTarget = target == null ? null : new Point(target);
        fidgetUntilMs = target == null ? 0L : Math.max(0L, untilMs);
    }
    public void clearFidget() {
        fidgetTarget = null;
        fidgetUntilMs = 0L;
    }
    public String blocker() { return blocker; }
    public void setBlocker(String blocker) { this.blocker = blocker == null ? "" : blocker; }
    public long blockerSinceMs() { return blockerSinceMs; }
    public int blockerAttempts() { return blockerAttempts; }
    public int observeBlocker(String reason, long nowMs) {
        String next = reason == null ? "unknown" : reason;
        if (!next.equals(blocker)) {
            blocker = next;
            blockerSinceMs = nowMs;
            blockerAttempts = 1;
        } else {
            blockerAttempts++;
        }
        return blockerAttempts;
    }
    public void clearBlocker() {
        blocker = "";
        blockerSinceMs = 0L;
        blockerAttempts = 0;
        nextRetryAtMs = 0L;
    }
    public long nextRetryAtMs() { return nextRetryAtMs; }
    public void setNextRetryAtMs(long value) { nextRetryAtMs = Math.max(0L, value); }
    public int enteredStage() { return enteredStage; }
    public long stageMovementNotBeforeMs() { return stageMovementNotBeforeMs; }
    public void beginStageMovement(int stage, long notBeforeMs) {
        if (enteredStage == stage) return;
        enteredStage = stage;
        stageMovementNotBeforeMs = Math.max(0L, notBeforeMs);
    }

}
