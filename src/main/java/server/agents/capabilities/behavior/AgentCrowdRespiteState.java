package server.agents.capabilities.behavior;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.awt.Point;

public final class AgentCrowdRespiteState {
    public static final AgentCapabilityStateKey<AgentCrowdRespiteState> STATE_KEY =
            new AgentCapabilityStateKey<>("behavior.crowd-respite", AgentCrowdRespiteState.class,
                    AgentCrowdRespiteState::new);

    private boolean active;
    private long startedAtMs;
    private long resumeAtMs;
    private Point safeSpot;
    private boolean chairPreferred;
    private boolean settledEventSent;
    private long nextEligibleAtMs;

    public synchronized void start(long nowMs, long resumeAtMs, Point safeSpot, boolean chairPreferred) {
        active = true;
        startedAtMs = nowMs;
        this.resumeAtMs = resumeAtMs;
        this.safeSpot = safeSpot == null ? null : new Point(safeSpot);
        this.chairPreferred = chairPreferred;
        settledEventSent = false;
    }

    public synchronized void clear() {
        active = false;
        startedAtMs = 0L;
        resumeAtMs = 0L;
        safeSpot = null;
        chairPreferred = false;
        settledEventSent = false;
        nextEligibleAtMs = 0L;
    }

    public synchronized void finish(long nextEligibleAtMs) {
        active = false;
        startedAtMs = 0L;
        resumeAtMs = 0L;
        safeSpot = null;
        chairPreferred = false;
        settledEventSent = false;
        this.nextEligibleAtMs = Math.max(0L, nextEligibleAtMs);
    }

    public synchronized boolean active() { return active; }
    public synchronized boolean eligible(long nowMs) { return nowMs >= nextEligibleAtMs; }
    public synchronized long resumeAtMs() { return resumeAtMs; }
    public synchronized Point safeSpot() { return safeSpot == null ? null : new Point(safeSpot); }
    public synchronized boolean chairPreferred() { return chairPreferred; }
    public synchronized boolean markSettledEventSent() {
        if (settledEventSent) return false;
        settledEventSent = true;
        return true;
    }
}
