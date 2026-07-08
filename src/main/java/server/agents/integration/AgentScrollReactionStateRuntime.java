package server.agents.integration;

import server.agents.capabilities.social.AgentScrollReactionState;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentScrollReactionStateRuntime {
    private AgentScrollReactionStateRuntime() {
    }

    public static boolean isOnCooldown(AgentRuntimeEntry entry, long nowMs) {
        return nowMs < entry.scrollReactionState().nextReactionAtMs();
    }

    public static void startCooldown(AgentRuntimeEntry entry, long nowMs, long cooldownMs) {
        entry.scrollReactionState().setNextReactionAtMs(nowMs + Math.max(0, cooldownMs));
    }

    public static double recordReactionLoad(AgentRuntimeEntry entry, long nowMs, long decayMs) {
        if (entry == null) {
            return 0.0;
        }

        long safeDecayMs = Math.max(1, decayMs);
        double load = entry.scrollReactionState().recentLoad();
        long lastObservedAtMs = entry.scrollReactionState().lastObservedAtMs();
        if (lastObservedAtMs > 0L && nowMs > lastObservedAtMs) {
            load *= Math.exp(-(double) (nowMs - lastObservedAtMs) / safeDecayMs);
        }
        load += 1.0;
        entry.scrollReactionState().setRecentLoad(load);
        entry.scrollReactionState().setLastObservedAtMs(nowMs);
        return load;
    }

    public static int updateReactionStreak(
            AgentRuntimeEntry entry,
            int scrollerId,
            boolean success,
            long nowMs,
            long windowMs,
            long pruneIntervalMs) {
        if (entry == null || scrollerId <= 0) {
            return 0;
        }

        pruneStreaks(entry, nowMs, windowMs, pruneIntervalMs);
        long safeWindowMs = Math.max(1, windowMs);
        AgentScrollReactionState.StreakState state = entry.scrollReactionState().streaksByScroller()
                .computeIfAbsent(scrollerId, ignored -> new AgentScrollReactionState.StreakState());
        if (state.lastOutcomeAtMs == 0L
                || nowMs - state.lastOutcomeAtMs > safeWindowMs
                || state.lastWasSuccess != success) {
            state.streak = 1;
        } else {
            state.streak++;
        }
        state.lastWasSuccess = success;
        state.lastOutcomeAtMs = nowMs;
        return state.streak;
    }

    public static void pruneStreaks(AgentRuntimeEntry entry, long nowMs, long windowMs, long pruneIntervalMs) {
        if (nowMs < entry.scrollReactionState().nextStreakPruneAtMs()) {
            return;
        }
        long cutoff = nowMs - windowMs;
        entry.scrollReactionState().streaksByScroller().entrySet()
                .removeIf(it -> it.getValue() == null || it.getValue().lastOutcomeAtMs < cutoff);
        entry.scrollReactionState().setNextStreakPruneAtMs(nowMs + pruneIntervalMs);
    }
}
