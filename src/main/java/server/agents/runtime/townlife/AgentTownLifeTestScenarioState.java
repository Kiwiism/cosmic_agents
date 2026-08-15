package server.agents.runtime.townlife;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.awt.Point;

/** Per-Agent state for the external cyclic TownLife test coordinator. */
public final class AgentTownLifeTestScenarioState {
    public static final AgentCapabilityStateKey<AgentTownLifeTestScenarioState> STATE_KEY =
            new AgentCapabilityStateKey<>("town-life.test-scenario",
                    AgentTownLifeTestScenarioState.class,
                    AgentTownLifeTestScenarioState::new);

    public enum Phase {
        INACTIVE,
        IN_TOWN_LIFE,
        STAGING,
        OUTSIDE_IDLE,
        STOPPING,
        COMPLETED,
        FAILED
    }

    private AgentTownLifeTestScenarioRequest request;
    private Phase phase = Phase.INACTIVE;
    private int cyclesStarted;
    private int cyclesCompleted;
    private long nextActionAtMs;
    private Point standbyPoint;
    private String failure = "";

    synchronized void start(AgentTownLifeTestScenarioRequest nextRequest) {
        request = nextRequest;
        phase = Phase.IN_TOWN_LIFE;
        cyclesStarted = 0;
        cyclesCompleted = 0;
        nextActionAtMs = 0L;
        standbyPoint = null;
        failure = "";
    }

    synchronized void visitStarted() {
        phase = Phase.IN_TOWN_LIFE;
        cyclesStarted++;
        nextActionAtMs = 0L;
        standbyPoint = null;
    }

    synchronized void visitCompleted(Point nextStandbyPoint) {
        cyclesCompleted++;
        phase = Phase.STAGING;
        standbyPoint = nextStandbyPoint == null ? null : new Point(nextStandbyPoint);
        nextActionAtMs = 0L;
    }

    synchronized void outsideIdle(long untilMs) {
        phase = Phase.OUTSIDE_IDLE;
        nextActionAtMs = Math.max(0L, untilMs);
    }

    synchronized void stopping() {
        phase = Phase.STOPPING;
    }

    synchronized void complete() {
        phase = Phase.COMPLETED;
        nextActionAtMs = 0L;
    }

    synchronized void fail(String reason) {
        phase = Phase.FAILED;
        failure = reason == null ? "" : reason.trim();
    }

    public synchronized boolean active() {
        return request != null && phase != Phase.INACTIVE
                && phase != Phase.COMPLETED && phase != Phase.FAILED;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(active(), request, phase, cyclesStarted, cyclesCompleted,
                nextActionAtMs, standbyPoint == null ? null : new Point(standbyPoint), failure);
    }

    public record Snapshot(boolean active,
                           AgentTownLifeTestScenarioRequest request,
                           Phase phase,
                           int cyclesStarted,
                           int cyclesCompleted,
                           long nextActionAtMs,
                           Point standbyPoint,
                           String failure) {
    }
}
