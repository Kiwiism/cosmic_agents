package server.agents.integration;

import server.agents.runtime.AgentTickState;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed tick cadence state.
 */
public final class AgentTickCadenceStateRuntime {
    private AgentTickCadenceStateRuntime() {
    }

    public static int skipDelayMs(AgentRuntimeEntry entry) {
        return state(entry).skipDelayMs();
    }

    public static int aiTickAccumulatorMs(AgentRuntimeEntry entry) {
        return state(entry).aiTickAccumulatorMs();
    }

    public static void setSkipDelayMs(AgentRuntimeEntry entry, int skipDelayMs) {
        state(entry).setSkipDelayMs(skipDelayMs);
    }

    public static void setAiTickAccumulatorMs(AgentRuntimeEntry entry, int aiTickAccumulatorMs) {
        state(entry).setAiTickAccumulatorMs(aiTickAccumulatorMs);
    }

    public static void reset(AgentRuntimeEntry entry) {
        state(entry).resetCadence();
    }

    public static boolean consumeSkipDelay(AgentRuntimeEntry entry, int tickMs) {
        int skipDelayMs = skipDelayMs(entry);
        if (skipDelayMs <= 0) {
            return false;
        }
        state(entry).setSkipDelayMs(Math.max(0, skipDelayMs - tickMs));
        return true;
    }

    public static boolean consumeAiTick(AgentRuntimeEntry entry, int tickMs, int aiTickMs) {
        int accumulator = aiTickAccumulatorMs(entry) + tickMs;
        if (accumulator < aiTickMs) {
            state(entry).setAiTickAccumulatorMs(accumulator);
            return false;
        }
        state(entry).setAiTickAccumulatorMs(accumulator - aiTickMs);
        return true;
    }

    private static AgentTickState state(AgentRuntimeEntry entry) {
        return entry.tickState();
    }
}
