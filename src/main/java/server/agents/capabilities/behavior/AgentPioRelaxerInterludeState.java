package server.agents.capabilities.behavior;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.awt.Point;

/** One-shot, session-local pause after Pio awards the Relaxer. */
public final class AgentPioRelaxerInterludeState {
    public static final AgentCapabilityStateKey<AgentPioRelaxerInterludeState> STATE_KEY =
            new AgentCapabilityStateKey<>("behavior.pio-relaxer-interlude",
                    AgentPioRelaxerInterludeState.class, AgentPioRelaxerInterludeState::new);

    public enum Mode { NONE, REST, PLAYFUL }
    public enum Stage { IDLE, WAITING_FOR_SPOT, MOVING, ACTIVE }

    private Mode mode = Mode.NONE;
    private Stage stage = Stage.IDLE;
    private long requestedAtMs;
    private long durationMs;
    private long resumeAtMs;
    private long nextToggleAtMs;
    private int toggleSequence;
    private Point target;

    public synchronized boolean request(Mode requestedMode, long requestedDurationMs, long nowMs) {
        if (requestedMode == null || requestedMode == Mode.NONE || requestedDurationMs <= 0L
                || stage != Stage.IDLE) {
            return false;
        }
        mode = requestedMode;
        stage = Stage.WAITING_FOR_SPOT;
        requestedAtMs = nowMs;
        durationMs = requestedDurationMs;
        return true;
    }

    public synchronized void assignSpot(Point position) {
        if (stage != Stage.WAITING_FOR_SPOT || position == null) {
            return;
        }
        target = new Point(position);
        stage = Stage.MOVING;
    }

    public synchronized void begin(long nowMs) {
        if (stage != Stage.MOVING) {
            return;
        }
        stage = Stage.ACTIVE;
        resumeAtMs = nowMs + durationMs;
        nextToggleAtMs = nowMs;
        toggleSequence = 0;
    }

    public synchronized void scheduleToggle(long nextAtMs) {
        nextToggleAtMs = nextAtMs;
        toggleSequence++;
    }

    public synchronized void clear() {
        mode = Mode.NONE;
        stage = Stage.IDLE;
        requestedAtMs = 0L;
        durationMs = 0L;
        resumeAtMs = 0L;
        nextToggleAtMs = 0L;
        toggleSequence = 0;
        target = null;
    }

    public synchronized boolean active() { return stage != Stage.IDLE; }
    public synchronized Mode mode() { return mode; }
    public synchronized Stage stage() { return stage; }
    public synchronized long requestedAtMs() { return requestedAtMs; }
    public synchronized long durationMs() { return durationMs; }
    public synchronized long resumeAtMs() { return resumeAtMs; }
    public synchronized long nextToggleAtMs() { return nextToggleAtMs; }
    public synchronized int toggleSequence() { return toggleSequence; }
    public synchronized Point target() { return target == null ? null : new Point(target); }
}
