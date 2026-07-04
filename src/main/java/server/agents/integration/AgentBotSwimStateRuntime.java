package server.agents.integration;

import server.bots.BotEntry;
import server.agents.capabilities.movement.AgentSwimIntentState;

/**
 * Agent-owned adapter for temporary BotEntry-backed swim intent state.
 */
public final class AgentBotSwimStateRuntime {
    private AgentBotSwimStateRuntime() {
    }

    public static boolean swimming(BotEntry entry) {
        return state(entry).swimming();
    }

    public static void setSwimming(BotEntry entry, boolean swimming) {
        state(entry).setSwimming(swimming);
    }

    public static int swimMoveDirection(BotEntry entry) {
        return state(entry).moveDirection();
    }

    public static void setSwimMoveDirection(BotEntry entry, int direction) {
        state(entry).setMoveDirection(direction);
    }

    public static int swimVerticalHold(BotEntry entry) {
        return state(entry).verticalHold();
    }

    public static void setSwimVerticalHold(BotEntry entry, int verticalHold) {
        state(entry).setVerticalHold(verticalHold);
    }

    public static boolean swimJumpRequested(BotEntry entry) {
        return state(entry).jumpRequested();
    }

    public static void setSwimJumpRequested(BotEntry entry, boolean requested) {
        state(entry).setJumpRequested(requested);
    }

    public static long swimNextJumpAtMs(BotEntry entry) {
        return state(entry).nextJumpAtMs();
    }

    public static void setSwimNextJumpAtMs(BotEntry entry, long nextJumpAtMs) {
        state(entry).setNextJumpAtMs(nextJumpAtMs);
    }

    public static void clearSwimInput(BotEntry entry) {
        state(entry).clearInput();
    }

    private static AgentSwimIntentState state(BotEntry entry) {
        return entry.swimIntentState();
    }
}
