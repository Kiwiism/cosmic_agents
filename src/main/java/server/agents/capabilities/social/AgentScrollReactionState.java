package server.agents.capabilities.social;

import java.util.HashMap;
import java.util.Map;

/**
 * Mutable state for social scroll reactions while live runtime storage is still being split.
 */
public final class AgentScrollReactionState {
    public static final class StreakState {
        public int streak = 0;
        public boolean lastWasSuccess = false;
        public long lastOutcomeAtMs = 0L;
    }

    private double recentLoad = 0.0;
    private long lastObservedAtMs = 0L;
    private long nextReactionAtMs = 0L;
    private final Map<Integer, StreakState> streaksByScroller = new HashMap<>();
    private long nextStreakPruneAtMs = 0L;

    public double recentLoad() {
        return recentLoad;
    }

    public void setRecentLoad(double recentLoad) {
        this.recentLoad = recentLoad;
    }

    public long lastObservedAtMs() {
        return lastObservedAtMs;
    }

    public void setLastObservedAtMs(long lastObservedAtMs) {
        this.lastObservedAtMs = lastObservedAtMs;
    }

    public long nextReactionAtMs() {
        return nextReactionAtMs;
    }

    public void setNextReactionAtMs(long nextReactionAtMs) {
        this.nextReactionAtMs = nextReactionAtMs;
    }

    public Map<Integer, StreakState> streaksByScroller() {
        return streaksByScroller;
    }

    public long nextStreakPruneAtMs() {
        return nextStreakPruneAtMs;
    }

    public void setNextStreakPruneAtMs(long nextStreakPruneAtMs) {
        this.nextStreakPruneAtMs = nextStreakPruneAtMs;
    }
}
