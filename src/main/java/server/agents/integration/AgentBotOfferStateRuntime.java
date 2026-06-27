package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned offer state adapter. Gear-prompt reservation state is still
 * backed by BotEntry during reconstruction, but callers should depend on this
 * narrow state boundary.
 */
public final class AgentBotOfferStateRuntime {
    private AgentBotOfferStateRuntime() {
    }

    public static boolean hasPendingGearPromptAfter(BotEntry entry, long nowMs) {
        return entry.pendingGearPromptAt() > nowMs;
    }

    public static long pendingGearPromptAt(BotEntry entry) {
        return entry.pendingGearPromptAt();
    }

    public static void reserveGearPrompt(BotEntry entry, long scheduledAt) {
        entry.setPendingGearPromptAt(scheduledAt);
    }

    public static boolean isReservedGearPrompt(BotEntry entry, long scheduledAt) {
        return entry.pendingGearPromptAt() == scheduledAt;
    }

    public static void clearGearPrompt(BotEntry entry) {
        entry.setPendingGearPromptAt(0L);
    }
}
