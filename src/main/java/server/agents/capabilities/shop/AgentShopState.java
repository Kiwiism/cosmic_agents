package server.agents.capabilities.shop;

import java.awt.Point;

/**
 * Mutable runtime state for one Agent shop visit/resupply sequence.
 */
public final class AgentShopState {
    private volatile boolean visitPending = false;
    private volatile Point npcPosition = null;
    private volatile Point targetPosition = null;
    private int approachDelayMs = 0;
    private boolean sequenceActive = false;
    private long visitStartedAtMs = 0L;
    private long sequenceStartedAtMs = 0L;
    private boolean sellTrashPending = false;
    private Point stuckCheckPosition = null;
    private long stuckCheckAtMs = 0L;

    public boolean visitPending() {
        return visitPending;
    }

    public boolean sequenceActive() {
        return sequenceActive;
    }

    public boolean hasActiveTransition() {
        return visitPending || sequenceActive;
    }

    public Point npcPosition() {
        return npcPosition == null ? null : new Point(npcPosition);
    }

    public Point targetPosition() {
        return targetPosition == null ? null : new Point(targetPosition);
    }

    public Point activeTargetPosition() {
        Point target = targetPosition != null ? targetPosition : npcPosition;
        return target == null ? null : new Point(target);
    }

    public int approachDelayMs() {
        return approachDelayMs;
    }

    public void setApproachDelayMs(int delayMs) {
        approachDelayMs = Math.max(0, delayMs);
    }

    public long visitStartedAtMs() {
        return visitStartedAtMs;
    }

    public long sequenceStartedAtMs() {
        return sequenceStartedAtMs;
    }

    public boolean sellTrashPending() {
        return sellTrashPending;
    }

    public void setSellTrashPending(boolean pending) {
        sellTrashPending = pending;
    }

    public boolean hasNpcPosition() {
        return npcPosition != null;
    }

    public boolean visitTimedOut(long nowMs, long timeoutMs) {
        return visitStartedAtMs > 0 && !sequenceActive && nowMs - visitStartedAtMs > timeoutMs;
    }

    public boolean sequenceTimedOut(long nowMs, long timeoutMs) {
        return sequenceActive && sequenceStartedAtMs > 0 && nowMs - sequenceStartedAtMs > timeoutMs;
    }

    public void startVisit(Point npcPosition, Point targetPosition, int approachDelayMs, long startedAtMs) {
        visitPending = true;
        this.npcPosition = npcPosition == null ? null : new Point(npcPosition);
        this.targetPosition = targetPosition == null ? null : new Point(targetPosition);
        setApproachDelayMs(approachDelayMs);
        visitStartedAtMs = startedAtMs;
        sequenceStartedAtMs = 0L;
    }

    public void markSequenceActive(long startedAtMs) {
        sequenceActive = true;
        sequenceStartedAtMs = startedAtMs;
    }

    public boolean stuckNearNpc(Point botPosition, long nowMs, long fallbackMs, int moveTolerancePx,
                                int arriveDistance) {
        if (npcPosition == null || botPosition == null) {
            return false;
        }
        if (stuckCheckPosition == null) {
            stuckCheckPosition = new Point(botPosition);
            stuckCheckAtMs = nowMs;
            return false;
        }
        if (botPosition.distanceSq(stuckCheckPosition) > (long) moveTolerancePx * moveTolerancePx) {
            stuckCheckPosition.setLocation(botPosition);
            stuckCheckAtMs = nowMs;
            return false;
        }
        if (nowMs - stuckCheckAtMs < fallbackMs) {
            return false;
        }
        return Math.abs(botPosition.x - npcPosition.x) + Math.abs(botPosition.y - npcPosition.y) <= arriveDistance;
    }

    public boolean sequenceValid(Point botPosition, Point npcPosition, int arriveDistance) {
        if (!visitPending || !sequenceActive || npcPosition == null || botPosition == null) {
            return false;
        }
        Point approach = targetPosition != null ? targetPosition : npcPosition;
        return manhattan(botPosition, approach) <= arriveDistance
                || manhattan(botPosition, npcPosition) <= arriveDistance;
    }

    public void clear() {
        visitPending = false;
        npcPosition = null;
        targetPosition = null;
        approachDelayMs = 0;
        sequenceActive = false;
        visitStartedAtMs = 0L;
        sequenceStartedAtMs = 0L;
        sellTrashPending = false;
        stuckCheckPosition = null;
        stuckCheckAtMs = 0L;
    }

    private static int manhattan(Point a, Point b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }
}
