package server.agents.capabilities.partyquest.kpq;

/** Mutable state bag for KPQ automation. One instance per live Agent runtime. */
public final class AgentKpqState {
    // Stage 1
    private int state = AgentKpqStage1.IDLE;
    private int couponTarget = -1;
    private long waitUntilMs;
    private int lastReportedCoupons;
    // Stage 5
    private boolean stage5Claimed;

    public int state() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public boolean stateIs(int state) {
        return this.state == state;
    }

    public boolean stateAtLeast(int state) {
        return this.state >= state;
    }

    public int couponTarget() {
        return couponTarget;
    }

    public void setCouponTarget(int couponTarget) {
        this.couponTarget = couponTarget;
    }

    public long waitUntilMs() {
        return waitUntilMs;
    }

    public void setWaitUntilMs(long waitUntilMs) {
        this.waitUntilMs = waitUntilMs;
    }

    public int lastReportedCoupons() {
        return lastReportedCoupons;
    }

    public void setLastReportedCoupons(int lastReportedCoupons) {
        this.lastReportedCoupons = lastReportedCoupons;
    }

    public boolean stage5Claimed() {
        return stage5Claimed;
    }

    public void markStage5Claimed() {
        stage5Claimed = true;
    }

    public void clearStage5Claimed() {
        stage5Claimed = false;
    }

    public void resetStage1(int idleState) {
        state = idleState;
        couponTarget = -1;
        waitUntilMs = 0;
        lastReportedCoupons = 0;
    }
}
