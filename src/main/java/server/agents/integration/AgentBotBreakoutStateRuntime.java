package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed surround-breakout state.
 */
public final class AgentBotBreakoutStateRuntime {
    private AgentBotBreakoutStateRuntime() {
    }

    public static boolean hasBreakoutCommitment(BotEntry entry) {
        return entry.breakoutState().hasCommitment();
    }

    public static int direction(BotEntry entry) {
        return entry.breakoutState().direction();
    }

    public static long untilMs(BotEntry entry) {
        return entry.breakoutState().untilMs();
    }

    public static void setBreakoutCommitment(BotEntry entry, int direction, long untilMs) {
        entry.breakoutState().setCommitment(direction, untilMs);
    }

    public static boolean isExpired(BotEntry entry, long nowMs) {
        return entry.breakoutState().expired(nowMs);
    }

    public static void clear(BotEntry entry) {
        entry.breakoutState().clear();
    }
}
