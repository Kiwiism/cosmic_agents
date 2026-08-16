package server.agents.runtime.field;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.awt.Point;

/** Per-Agent managed field lifecycle; map allocation remains in AgentFieldRuntime. */
public final class AgentFieldActivityState {
    public static final AgentCapabilityStateKey<AgentFieldActivityState> STATE_KEY =
            new AgentCapabilityStateKey<>("runtime.field-activity",
                    AgentFieldActivityState.class, AgentFieldActivityState::new);

    public enum Phase { IDLE, GRINDING, RESTING, SUSPENDED, DRAINING }

    private AgentFieldSessionHandle handle;
    private AgentFieldVisitRequest visit;
    private Phase phase = Phase.IDLE;
    private String exitReason = "";
    private long exitDeadlineMs;
    private Point restTarget;
    private long restUntilMs;
    private String restReason = "";
    private boolean restArrived;

    public synchronized void start(AgentFieldSessionHandle next, AgentFieldVisitRequest request) {
        handle = next;
        visit = request;
        phase = Phase.GRINDING;
        clearTransient();
    }

    public synchronized boolean active() {
        return handle != null && visit != null && phase != Phase.IDLE;
    }

    public synchronized AgentFieldSessionHandle handle() { return handle; }
    public synchronized AgentFieldVisitRequest visit() { return visit; }
    public synchronized Phase phase() { return phase; }
    public synchronized String exitReason() { return exitReason; }
    public synchronized long exitDeadlineMs() { return exitDeadlineMs; }
    public synchronized Point restTarget() { return restTarget == null ? null : new Point(restTarget); }
    public synchronized long restUntilMs() { return restUntilMs; }
    public synchronized String restReason() { return restReason; }
    public synchronized boolean restArrived() { return restArrived; }

    public synchronized void drain(String reason, long deadlineMs) {
        phase = Phase.DRAINING;
        exitReason = reason == null ? "" : reason.trim();
        exitDeadlineMs = Math.max(0L, deadlineMs);
        restTarget = null;
        restUntilMs = 0L;
    }

    public synchronized void suspend() {
        if (active() && phase != Phase.DRAINING) phase = Phase.SUSPENDED;
    }

    public synchronized void resume() {
        if (active() && phase == Phase.SUSPENDED) phase = Phase.GRINDING;
    }

    public synchronized void rest(Point target, long untilMs, String reason) {
        if (!active() || target == null) return;
        phase = Phase.RESTING;
        restTarget = new Point(target);
        restUntilMs = Math.max(0L, untilMs);
        restReason = reason == null ? "" : reason.trim();
        restArrived = false;
    }

    public synchronized void arriveRest() { restArrived = true; }

    public synchronized void completeRest() {
        if (active() && phase == Phase.RESTING) phase = Phase.GRINDING;
        restTarget = null;
        restUntilMs = 0L;
        restReason = "";
        restArrived = false;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(active(), handle, visit, phase, exitReason, exitDeadlineMs,
                restTarget == null ? null : new Point(restTarget), restUntilMs, restReason);
    }

    public synchronized void clear() {
        handle = null;
        visit = null;
        phase = Phase.IDLE;
        clearTransient();
    }

    private void clearTransient() {
        exitReason = "";
        exitDeadlineMs = 0L;
        restTarget = null;
        restUntilMs = 0L;
        restReason = "";
        restArrived = false;
    }

    public record Snapshot(boolean active, AgentFieldSessionHandle handle,
                           AgentFieldVisitRequest visit, Phase phase,
                           String exitReason, long exitDeadlineMs,
                           Point restTarget, long restUntilMs, String restReason) {
        public Snapshot {
            restTarget = restTarget == null ? null : new Point(restTarget);
        }

        @Override
        public Point restTarget() { return restTarget == null ? null : new Point(restTarget); }
    }
}
