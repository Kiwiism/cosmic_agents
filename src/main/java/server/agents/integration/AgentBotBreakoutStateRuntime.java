package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed surround-breakout state.
 */
public final class AgentBotBreakoutStateRuntime {
    private AgentBotBreakoutStateRuntime() {
    }

    public static boolean hasBreakoutCommitment(BotEntry entry) {
        return entry.hasBreakoutCommitment();
    }

    public static int direction(BotEntry entry) {
        return entry.breakoutDirection();
    }

    public static long untilMs(BotEntry entry) {
        return entry.breakoutUntilMs();
    }

    public static void setBreakoutCommitment(BotEntry entry, int direction, long untilMs) {
        entry.setBreakoutCommitment(direction, untilMs);
    }

    public static boolean isExpired(BotEntry entry, long nowMs) {
        return nowMs >= untilMs(entry);
    }

    public static void clear(BotEntry entry) {
        entry.clearBreakoutCommitment();
    }
}
