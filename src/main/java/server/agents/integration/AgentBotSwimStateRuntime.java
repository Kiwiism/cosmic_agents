package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed swim intent state.
 */
public final class AgentBotSwimStateRuntime {
    private AgentBotSwimStateRuntime() {
    }

    public static boolean swimming(BotEntry entry) {
        return entry.swimming();
    }

    public static void setSwimming(BotEntry entry, boolean swimming) {
        entry.setSwimming(swimming);
    }

    public static int swimMoveDirection(BotEntry entry) {
        return entry.swimMoveDirection();
    }

    public static void setSwimMoveDirection(BotEntry entry, int direction) {
        entry.setSwimMoveDirection(direction);
    }

    public static int swimVerticalHold(BotEntry entry) {
        return entry.swimVerticalHold();
    }

    public static void setSwimVerticalHold(BotEntry entry, int verticalHold) {
        entry.setSwimVerticalHold(verticalHold);
    }

    public static boolean swimJumpRequested(BotEntry entry) {
        return entry.swimJumpRequested();
    }

    public static void setSwimJumpRequested(BotEntry entry, boolean requested) {
        entry.setSwimJumpRequested(requested);
    }

    public static long swimNextJumpAtMs(BotEntry entry) {
        return entry.swimNextJumpAtMs();
    }

    public static void setSwimNextJumpAtMs(BotEntry entry, long nextJumpAtMs) {
        entry.setSwimNextJumpAtMs(nextJumpAtMs);
    }

    public static void clearSwimInput(BotEntry entry) {
        entry.setSwimMoveDirection(0);
        entry.setSwimVerticalHold(0);
        entry.setSwimJumpRequested(false);
    }
}
