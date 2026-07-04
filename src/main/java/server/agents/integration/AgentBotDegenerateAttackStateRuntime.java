package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed ranged degenerate-hit state.
 */
public final class AgentBotDegenerateAttackStateRuntime {
    private AgentBotDegenerateAttackStateRuntime() {
    }

    public static boolean degenAttackDone(BotEntry entry) {
        return entry.degenerateAttackState().done();
    }

    public static void markDegenAttackDone(BotEntry entry) {
        entry.degenerateAttackState().markDone();
    }

    public static void clear(BotEntry entry) {
        entry.degenerateAttackState().clear();
    }
}
