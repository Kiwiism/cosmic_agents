package server.agents.integration;

import server.agents.capabilities.movement.AgentSwimIntentState;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for temporary AgentRuntimeEntry-backed swim intent state.
 */
public final class AgentSwimStateRuntime {
    private AgentSwimStateRuntime() {
    }

    public static boolean swimming(AgentRuntimeEntry entry) {
        return state(entry).swimming();
    }

    public static void setSwimming(AgentRuntimeEntry entry, boolean swimming) {
        state(entry).setSwimming(swimming);
    }

    public static int swimMoveDirection(AgentRuntimeEntry entry) {
        return state(entry).moveDirection();
    }

    public static void setSwimMoveDirection(AgentRuntimeEntry entry, int direction) {
        state(entry).setMoveDirection(direction);
    }

    public static int swimVerticalHold(AgentRuntimeEntry entry) {
        return state(entry).verticalHold();
    }

    public static void setSwimVerticalHold(AgentRuntimeEntry entry, int verticalHold) {
        state(entry).setVerticalHold(verticalHold);
    }

    public static boolean swimJumpRequested(AgentRuntimeEntry entry) {
        return state(entry).jumpRequested();
    }

    public static void setSwimJumpRequested(AgentRuntimeEntry entry, boolean requested) {
        state(entry).setJumpRequested(requested);
    }

    public static long swimNextJumpAtMs(AgentRuntimeEntry entry) {
        return state(entry).nextJumpAtMs();
    }

    public static void setSwimNextJumpAtMs(AgentRuntimeEntry entry, long nextJumpAtMs) {
        state(entry).setNextJumpAtMs(nextJumpAtMs);
    }

    public static void clearSwimInput(AgentRuntimeEntry entry) {
        state(entry).clearInput();
    }

    private static AgentSwimIntentState state(AgentRuntimeEntry entry) {
        return entry.swimIntentState();
    }
}
