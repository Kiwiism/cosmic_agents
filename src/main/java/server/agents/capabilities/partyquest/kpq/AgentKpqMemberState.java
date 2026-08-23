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
    private boolean blockerRecoveryAttempted;
    private long nextRetryAtMs;
    private int enteredStage;
    private long stageMovementNotBeforeMs;
    private long stage5BossBaselineAttacks = -1L;
    private long stage5BossBaselineHitLines = -1L;
    private long stage5BossBaselineMissLines = -1L;
    private long stage5BossBaselineDamage = -1L;
    private boolean stage1RopeObserved;
    private boolean stage1DescendingRopeObserved;
    private int stage1LastRopeY;
    private Point stage1LandingSafetyTarget;
    private long stage1LandingSafetyDeadlineMs;
    private boolean stage1ManagedDarkSight;

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
            blockerRecoveryAttempted = false;
        } else {
            blockerAttempts++;
        }
        return blockerAttempts;
    }
    public boolean blockerRecoveryAttempted() { return blockerRecoveryAttempted; }
    public void markBlockerRecoveryAttempted() { blockerRecoveryAttempted = true; }
    public void clearBlocker() {
        blocker = "";
        blockerSinceMs = 0L;
        blockerAttempts = 0;
        blockerRecoveryAttempted = false;
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

    boolean stage1RopeObserved() { return stage1RopeObserved; }
    boolean stage1DescendingRopeObserved() { return stage1DescendingRopeObserved; }
    int stage1LastRopeY() { return stage1LastRopeY; }
    void observeStage1Rope(int y, boolean descending) {
        stage1RopeObserved = true;
        stage1DescendingRopeObserved |= descending;
        stage1LastRopeY = y;
    }
    void clearStage1RopeObservation() {
        stage1RopeObserved = false;
        stage1DescendingRopeObserved = false;
        stage1LastRopeY = 0;
    }
    Point stage1LandingSafetyTarget() {
        return stage1LandingSafetyTarget == null ? null : new Point(stage1LandingSafetyTarget);
    }
    long stage1LandingSafetyDeadlineMs() { return stage1LandingSafetyDeadlineMs; }
    boolean stage1ManagedDarkSight() { return stage1ManagedDarkSight; }
    void beginStage1LandingSafety(Point target, long deadlineMs, boolean managedDarkSight) {
        stage1LandingSafetyTarget = target == null ? null : new Point(target);
        stage1LandingSafetyDeadlineMs = target == null ? 0L : Math.max(0L, deadlineMs);
        stage1ManagedDarkSight = target != null && managedDarkSight;
    }
    void clearStage1LandingSafety() {
        stage1LandingSafetyTarget = null;
        stage1LandingSafetyDeadlineMs = 0L;
        stage1ManagedDarkSight = false;
    }

    public boolean hasStage5BossCombatBaseline() {
        return stage5BossBaselineAttacks >= 0L;
    }

    public void beginStage5BossCombat(long attacks, long hitLines, long missLines, long damage) {
        if (hasStage5BossCombatBaseline()) return;
        stage5BossBaselineAttacks = Math.max(0L, attacks);
        stage5BossBaselineHitLines = Math.max(0L, hitLines);
        stage5BossBaselineMissLines = Math.max(0L, missLines);
        stage5BossBaselineDamage = Math.max(0L, damage);
    }

    public BossCombatDelta stage5BossCombatDelta(
            long attacks, long hitLines, long missLines, long damage) {
        if (!hasStage5BossCombatBaseline()) return new BossCombatDelta(0L, 0L, 0L, 0L);
        return new BossCombatDelta(
                delta(attacks, stage5BossBaselineAttacks),
                delta(hitLines, stage5BossBaselineHitLines),
                delta(missLines, stage5BossBaselineMissLines),
                delta(damage, stage5BossBaselineDamage));
    }

    public void resetStage5BossCombat() {
        stage5BossBaselineAttacks = -1L;
        stage5BossBaselineHitLines = -1L;
        stage5BossBaselineMissLines = -1L;
        stage5BossBaselineDamage = -1L;
    }

    private static long delta(long value, long baseline) {
        return Math.max(0L, value - baseline);
    }

    public record BossCombatDelta(long attacks, long hitLines, long missLines, long damage) {
    }

}
