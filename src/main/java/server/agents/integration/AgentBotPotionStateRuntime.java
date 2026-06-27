package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed potion sharing state.
 */
public final class AgentBotPotionStateRuntime {
    private AgentBotPotionStateRuntime() {
    }

    public static boolean potShareRequested(BotEntry entry, boolean forHp) {
        return forHp ? entry.potShareRequestedHp() : entry.potShareRequestedMp();
    }

    public static void setPotShareRequested(BotEntry entry, boolean forHp, boolean requested) {
        if (forHp) {
            entry.setPotShareRequestedHp(requested);
        } else {
            entry.setPotShareRequestedMp(requested);
        }
    }

    public static void clearPotShareRequested(BotEntry entry, boolean forHp) {
        setPotShareRequested(entry, forHp, false);
    }

    public static void clearAllPotShareRequests(BotEntry entry) {
        entry.setPotShareRequestedHp(false);
        entry.setPotShareRequestedMp(false);
    }
}
