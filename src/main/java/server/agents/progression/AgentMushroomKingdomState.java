package server.agents.progression;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.awt.Point;

/** Resumable live state for one Mushroom Kingdom visit. */
public final class AgentMushroomKingdomState {
    public enum Phase { ACTIVE, COMPLETE, BLOCKED }

    public static final AgentCapabilityStateKey<AgentMushroomKingdomState> STATE_KEY =
            new AgentCapabilityStateKey<>("progression.mushroom-kingdom",
                    AgentMushroomKingdomState.class, AgentMushroomKingdomState::new);

    private Phase phase = Phase.ACTIVE;
    private String reason = "";
    private int currentQuestId;
    private int observedMetric = -1;
    private int observedMapId;
    private Point observedPosition;
    private long progressAtMs;
    private long nextActionAtMs;
    private int capabilityFailures;

    public synchronized void begin(long nowMs) {
        phase = Phase.ACTIVE;
        reason = "starting Mushroom Kingdom questline";
        currentQuestId = 0;
        observedMetric = -1;
        observedMapId = 0;
        observedPosition = null;
        progressAtMs = nowMs;
        nextActionAtMs = 0L;
        capabilityFailures = 0;
    }

    public synchronized void observe(int questId, int metric, int mapId, Point position, long nowMs) {
        Point safe = position == null ? null : new Point(position);
        boolean moved = observedPosition == null || safe == null
                || observedPosition.distanceSq(safe) >= 24L * 24L;
        if (currentQuestId != questId || metric != observedMetric || observedMapId != mapId || moved) {
            progressAtMs = nowMs;
            capabilityFailures = 0;
        }
        currentQuestId = questId;
        observedMetric = metric;
        observedMapId = mapId;
        observedPosition = safe;
    }

    public synchronized void active(String reason) {
        phase = Phase.ACTIVE;
        this.reason = reason == null ? "" : reason;
    }

    public synchronized void complete(String reason) {
        phase = Phase.COMPLETE;
        this.reason = reason == null ? "" : reason;
    }

    public synchronized void block(String reason) {
        phase = Phase.BLOCKED;
        this.reason = reason == null ? "" : reason;
    }

    public synchronized Phase phase() { return phase; }
    public synchronized String reason() { return reason; }
    public synchronized int currentQuestId() { return currentQuestId; }
    public synchronized long progressAtMs() { return progressAtMs; }
    public synchronized long nextActionAtMs() { return nextActionAtMs; }
    public synchronized void nextActionAtMs(long value) { nextActionAtMs = value; }
    public synchronized int capabilityFailure() { return ++capabilityFailures; }
    public synchronized void capabilityProgress() { capabilityFailures = 0; }
}
