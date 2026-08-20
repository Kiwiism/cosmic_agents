package server.agents.capabilities.partyquest.kpq;

/** Pure scheduling policy for Stage 1's rotating map-wide coupon collector. */
final class AgentKpqCouponCollectionPolicy {
    private AgentKpqCouponCollectionPolicy() {
    }

    static Decision decide(long nowMs,
                           long stageEnteredAtMs,
                           long sweepStartedAtMs,
                           long nextSweepAtMs,
                           int couponsOnGround,
                           int remainingPartyNeed,
                           long sweepIntervalMs,
                           long sweepMaximumMs) {
        long nextSweep = nextSweepAtMs > 0L
                ? nextSweepAtMs
                : stageEnteredAtMs + sweepIntervalMs;
        if (remainingPartyNeed <= 0) {
            return new Decision(false, false, 0L, nowMs + sweepIntervalMs);
        }
        long sweepStarted = sweepStartedAtMs;
        if (couponsOnGround <= 0) {
            if (sweepStarted > 0L || nowMs >= nextSweep) {
                nextSweep = nowMs + sweepIntervalMs;
            }
            return new Decision(false, false, 0L, nextSweep);
        }
        if (sweepStarted > 0L) {
            boolean rotateCollector = nowMs - sweepStarted >= sweepMaximumMs;
            return new Decision(true, rotateCollector,
                    rotateCollector ? nowMs : sweepStarted, nextSweep);
        }
        if (nowMs >= nextSweep) {
            return new Decision(true, true, nowMs, nextSweep);
        }
        return new Decision(false, false, 0L, nextSweep);
    }

    record Decision(boolean sweepActive,
                    boolean rotateCollector,
                    long sweepStartedAtMs,
                    long nextSweepAtMs) {
    }
}
