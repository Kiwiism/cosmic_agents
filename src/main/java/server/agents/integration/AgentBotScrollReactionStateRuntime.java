package server.agents.integration;

import server.agents.capabilities.social.AgentScrollReactionState;
import server.bots.BotEntry;

public final class AgentBotScrollReactionStateRuntime {
    private AgentBotScrollReactionStateRuntime() {
    }

    public static boolean isOnCooldown(BotEntry entry, long nowMs) {
        return nowMs < entry.nextScrollReactionAtMs();
    }

    public static void startCooldown(BotEntry entry, long nowMs, long cooldownMs) {
        entry.setNextScrollReactionAtMs(nowMs + Math.max(0, cooldownMs));
    }

    public static double recordReactionLoad(BotEntry entry, long nowMs, long decayMs) {
        if (entry == null) {
            return 0.0;
        }

        long safeDecayMs = Math.max(1, decayMs);
        double load = entry.recentScrollReactionLoad();
        long lastObservedAtMs = entry.lastScrollReactionObservedAtMs();
        if (lastObservedAtMs > 0L && nowMs > lastObservedAtMs) {
            load *= Math.exp(-(double) (nowMs - lastObservedAtMs) / safeDecayMs);
        }
        load += 1.0;
        entry.setRecentScrollReactionLoad(load);
        entry.setLastScrollReactionObservedAtMs(nowMs);
        return load;
    }

    public static int updateReactionStreak(
            BotEntry entry,
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
        AgentScrollReactionState.StreakState state = entry.scrollReactionStreaksByScroller()
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

    public static void pruneStreaks(BotEntry entry, long nowMs, long windowMs, long pruneIntervalMs) {
        if (nowMs < entry.nextScrollReactionStreakPruneAtMs()) {
            return;
        }
        long cutoff = nowMs - windowMs;
        entry.scrollReactionStreaksByScroller().entrySet()
                .removeIf(it -> it.getValue() == null || it.getValue().lastOutcomeAtMs < cutoff);
        entry.setNextScrollReactionStreakPruneAtMs(nowMs + pruneIntervalMs);
    }
}
