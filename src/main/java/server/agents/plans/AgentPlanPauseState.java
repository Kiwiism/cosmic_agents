package server.agents.plans;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.HashMap;
import java.util.Map;

/** Multi-reason pause clock. Objective identity and progress remain untouched while paused. */
public final class AgentPlanPauseState {
    public static final AgentCapabilityStateKey<AgentPlanPauseState> STATE_KEY =
            new AgentCapabilityStateKey<>("plans.pause-clock", AgentPlanPauseState.class, AgentPlanPauseState::new);

    private final Map<String, Long> reasons = new HashMap<>();
    private long accumulatedPausedMs;
    private long pauseWindowStartedAtMs = -1L;

    public synchronized void pause(String reason, long nowMs) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("pause reason is required");
        if (reasons.isEmpty()) pauseWindowStartedAtMs = nowMs;
        reasons.putIfAbsent(reason, nowMs);
    }

    public synchronized void resume(String reason, long nowMs) {
        Long started = reasons.remove(reason);
        if (started != null && reasons.isEmpty() && pauseWindowStartedAtMs >= 0L) {
            accumulatedPausedMs += Math.max(0L, nowMs - pauseWindowStartedAtMs);
            pauseWindowStartedAtMs = -1L;
        }
    }

    public synchronized boolean paused() { return !reasons.isEmpty(); }

    public synchronized long effectiveNow(long wallNowMs) {
        long activePause = reasons.isEmpty() || pauseWindowStartedAtMs < 0L
                ? 0L : Math.max(0L, wallNowMs - pauseWindowStartedAtMs);
        return Math.max(0L, wallNowMs - accumulatedPausedMs - activePause);
    }

    public synchronized Map<String, Long> reasons() { return Map.copyOf(reasons); }
}
