package server.agents.capabilities.shop;

import java.awt.Point;

/**
 * Mutable runtime state for one Agent shop visit/resupply sequence.
 */
public final class AgentShopState {
    private final AgentShopWorkflow workflow = new AgentShopWorkflow();
    private volatile boolean visitPending = false;
    private volatile Point npcPosition = null;
    private volatile Point targetPosition = null;
    private int approachDelayMs = 0;
    private boolean sequenceActive = false;
    private long visitStartedAtMs = 0L;
    private long sequenceStartedAtMs = 0L;
    private boolean sellTrashPending = false;
    private int minimumMesoReserve = 0;
    private int requiredItemId = 0;
    private int requiredItemCount = 0;
    private int lastRequiredItemId = 0;
    private int lastRequiredItemCount = 0;
    private Point stuckCheckPosition = null;
    private long stuckCheckAtMs = 0L;

    public boolean visitPending() {
        return visitPending;
    }

    public AgentShopWorkflow workflow() {
        return workflow;
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

    public int minimumMesoReserve() {
        return minimumMesoReserve;
    }

    public void ensureMinimumMesoReserve(int mesos) {
        minimumMesoReserve = Math.max(minimumMesoReserve, Math.max(0, mesos));
    }

    public int requiredItemId() {
        return requiredItemId;
    }

    public int requiredItemCount() {
        return requiredItemCount;
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
        startVisit(npcPosition, targetPosition, approachDelayMs, 0, startedAtMs);
    }

    public void startVisit(Point npcPosition,
                           Point targetPosition,
                           int approachDelayMs,
                           int minimumMesoReserve,
                           long startedAtMs) {
        startVisit(npcPosition, targetPosition, approachDelayMs, minimumMesoReserve, 0, 0, startedAtMs);
    }

    public void startVisit(Point npcPosition,
                           Point targetPosition,
                           int approachDelayMs,
                           int minimumMesoReserve,
                           int requiredItemId,
                           int requiredItemCount,
                           long startedAtMs) {
        if (workflow.phase() == AgentShopWorkflowPhase.IDLE || workflow.phase().terminal()) {
            workflow.start("shop:" + startedAtMs, 0, startedAtMs);
            workflow.transition(AgentShopWorkflowPhase.APPROACHING, "travelling to shop NPC", startedAtMs);
        }
        visitPending = true;
        this.npcPosition = npcPosition == null ? null : new Point(npcPosition);
        this.targetPosition = targetPosition == null ? null : new Point(targetPosition);
        setApproachDelayMs(approachDelayMs);
        this.minimumMesoReserve = Math.max(0, minimumMesoReserve);
        this.requiredItemId = Math.max(0, requiredItemId);
        this.requiredItemCount = Math.max(0, requiredItemCount);
        lastRequiredItemId = 0;
        lastRequiredItemCount = 0;
        visitStartedAtMs = startedAtMs;
        sequenceStartedAtMs = 0L;
    }

    public void markSequenceActive(long startedAtMs) {
        workflow.transition(AgentShopWorkflowPhase.TRANSACTING, "shop transaction opened", startedAtMs);
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
        lastRequiredItemId = requiredItemId;
        lastRequiredItemCount = requiredItemCount;
        if (!workflow.phase().terminal() && workflow.phase() != AgentShopWorkflowPhase.IDLE) {
            workflow.transition(AgentShopWorkflowPhase.CANCELLED, "legacy shop state cleared",
                    Math.max(System.currentTimeMillis(), workflow.updatedAtMs()));
        }
        visitPending = false;
        npcPosition = null;
        targetPosition = null;
        approachDelayMs = 0;
        sequenceActive = false;
        visitStartedAtMs = 0L;
        sequenceStartedAtMs = 0L;
        sellTrashPending = false;
        minimumMesoReserve = 0;
        requiredItemId = 0;
        requiredItemCount = 0;
        stuckCheckPosition = null;
        stuckCheckAtMs = 0L;
    }

    public boolean lastVisitRequestedItem(int itemId, int itemCount) {
        return itemId > 0
                && itemCount > 0
                && lastRequiredItemId == itemId
                && lastRequiredItemCount == itemCount;
    }

    public void complete(String reason, long nowMs) {
        if (!workflow.phase().terminal() && workflow.phase() != AgentShopWorkflowPhase.IDLE) {
            workflow.transition(AgentShopWorkflowPhase.COMPLETED, reason, Math.max(nowMs, workflow.updatedAtMs()));
        }
    }

    public void block(String reason, long nowMs) {
        if (!workflow.phase().terminal() && workflow.phase() != AgentShopWorkflowPhase.IDLE) {
            workflow.transition(AgentShopWorkflowPhase.BLOCKED, reason, Math.max(nowMs, workflow.updatedAtMs()));
        }
    }

    private static int manhattan(Point a, Point b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }
}
