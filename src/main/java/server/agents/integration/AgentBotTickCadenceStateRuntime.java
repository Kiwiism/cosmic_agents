package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed tick cadence state.
 */
public final class AgentBotTickCadenceStateRuntime {
    private AgentBotTickCadenceStateRuntime() {
    }

    public static int skipDelayMs(BotEntry entry) {
        return entry.skipDelayMs();
    }

    public static int aiTickAccumulatorMs(BotEntry entry) {
        return entry.aiTickAccumulatorMs();
    }

    public static void reset(BotEntry entry) {
        entry.setSkipDelayMs(0);
        entry.setAiTickAccumulatorMs(0);
    }

    public static boolean consumeSkipDelay(BotEntry entry, int tickMs) {
        int skipDelayMs = skipDelayMs(entry);
        if (skipDelayMs <= 0) {
            return false;
        }
        entry.setSkipDelayMs(Math.max(0, skipDelayMs - tickMs));
        return true;
    }

    public static boolean consumeAiTick(BotEntry entry, int tickMs, int aiTickMs) {
        int accumulator = aiTickAccumulatorMs(entry) + tickMs;
        if (accumulator < aiTickMs) {
            entry.setAiTickAccumulatorMs(accumulator);
            return false;
        }
        entry.setAiTickAccumulatorMs(accumulator - aiTickMs);
        return true;
    }
}
