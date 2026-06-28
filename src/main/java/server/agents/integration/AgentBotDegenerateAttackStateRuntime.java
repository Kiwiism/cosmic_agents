package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed ranged degenerate-hit state.
 */
public final class AgentBotDegenerateAttackStateRuntime {
    private AgentBotDegenerateAttackStateRuntime() {
    }

    public static boolean degenAttackDone(BotEntry entry) {
        return entry.degenAttackDone();
    }

    public static void markDegenAttackDone(BotEntry entry) {
        entry.markDegenAttackDone();
    }

    public static void clear(BotEntry entry) {
        entry.clearDegenAttackDone();
    }
}
