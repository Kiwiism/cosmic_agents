package server.agents.integration;

import server.agents.runtime.AgentTickState;
import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed tick cadence state.
 */
public final class AgentBotTickCadenceStateRuntime {
    private AgentBotTickCadenceStateRuntime() {
    }

    public static int skipDelayMs(BotEntry entry) {
        return state(entry).skipDelayMs();
    }

    public static int aiTickAccumulatorMs(BotEntry entry) {
        return state(entry).aiTickAccumulatorMs();
    }

    public static void reset(BotEntry entry) {
        state(entry).resetCadence();
    }

    public static boolean consumeSkipDelay(BotEntry entry, int tickMs) {
        int skipDelayMs = skipDelayMs(entry);
        if (skipDelayMs <= 0) {
            return false;
        }
        state(entry).setSkipDelayMs(Math.max(0, skipDelayMs - tickMs));
        return true;
    }

    public static boolean consumeAiTick(BotEntry entry, int tickMs, int aiTickMs) {
        int accumulator = aiTickAccumulatorMs(entry) + tickMs;
        if (accumulator < aiTickMs) {
            state(entry).setAiTickAccumulatorMs(accumulator);
            return false;
        }
        state(entry).setAiTickAccumulatorMs(accumulator - aiTickMs);
        return true;
    }

    private static AgentTickState state(BotEntry entry) {
        return entry.tickState();
    }
}
