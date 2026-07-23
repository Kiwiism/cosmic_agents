package server.agents.capabilities.townlife;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Bounded micro-phases for a selected TownLife activity. */
public final class AgentTownLifeActivitySequenceState {
    public static final AgentCapabilityStateKey<AgentTownLifeActivitySequenceState> STATE_KEY =
            new AgentCapabilityStateKey<>("town-life.activity-sequence",
                    AgentTownLifeActivitySequenceState.class,
                    AgentTownLifeActivitySequenceState::new);

    public enum Phase {
        IDLE,
        ORIENT,
        OPENING,
        PERFORMING,
        REACTION,
        CLOSING,
        COMPLETE
    }

    private Phase phase = Phase.IDLE;
    private long orientEndMs;
    private long openingEndMs;
    private long reactionStartMs;
    private long closingStartMs;
    private long endMs;
    private boolean performanceStarted;

    public synchronized void start(long nowMs, long requestedEndMs) {
        long duration = Math.max(2_000L, requestedEndMs - nowMs);
        endMs = nowMs + duration;
        long orient = Math.min(700L, Math.max(200L, duration / 12));
        long opening = Math.min(900L, Math.max(250L, duration / 10));
        long closing = Math.min(800L, Math.max(250L, duration / 12));
        long reaction = Math.min(1_200L, Math.max(300L, duration / 8));
        orientEndMs = nowMs + orient;
        openingEndMs = orientEndMs + opening;
        closingStartMs = Math.max(openingEndMs, endMs - closing);
        reactionStartMs = Math.max(openingEndMs, closingStartMs - reaction);
        phase = Phase.ORIENT;
        performanceStarted = false;
    }

    public synchronized Phase advance(long nowMs) {
        phase = phaseAt(nowMs);
        return phase;
    }

    public synchronized Phase phase() {
        return phase;
    }

    public synchronized boolean performanceStarted() {
        return performanceStarted;
    }

    public synchronized void markPerformanceStarted() {
        performanceStarted = true;
    }

    public synchronized void clear() {
        phase = Phase.IDLE;
        orientEndMs = 0L;
        openingEndMs = 0L;
        reactionStartMs = 0L;
        closingStartMs = 0L;
        endMs = 0L;
        performanceStarted = false;
    }

    private Phase phaseAt(long nowMs) {
        if (phase == Phase.IDLE || endMs <= 0L) {
            return Phase.IDLE;
        }
        if (nowMs < orientEndMs) {
            return Phase.ORIENT;
        }
        if (nowMs < openingEndMs) {
            return Phase.OPENING;
        }
        if (nowMs < reactionStartMs) {
            return Phase.PERFORMING;
        }
        if (nowMs < closingStartMs) {
            return Phase.REACTION;
        }
        return nowMs < endMs ? Phase.CLOSING : Phase.COMPLETE;
    }
}
