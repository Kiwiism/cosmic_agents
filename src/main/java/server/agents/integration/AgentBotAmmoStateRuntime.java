package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed ammo sharing state.
 */
public final class AgentBotAmmoStateRuntime {
    private AgentBotAmmoStateRuntime() {
    }

    public static boolean ammoShareRequested(BotEntry entry) {
        return entry.ammoShareRequested();
    }

    public static void setAmmoShareRequested(BotEntry entry, boolean requested) {
        entry.setAmmoShareRequested(requested);
    }

    public static void clearAmmoShareRequested(BotEntry entry) {
        entry.setAmmoShareRequested(false);
    }
}
