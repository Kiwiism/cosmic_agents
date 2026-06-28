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

    public static boolean noAmmo(BotEntry entry) {
        return entry.noAmmo();
    }

    public static void setNoAmmo(BotEntry entry, boolean noAmmo) {
        entry.setNoAmmo(noAmmo);
    }

    public static boolean ammoWarnSent(BotEntry entry) {
        return entry.ammoWarnSent();
    }

    public static void setAmmoWarnSent(BotEntry entry, boolean ammoWarnSent) {
        entry.setAmmoWarnSent(ammoWarnSent);
    }

    public static void clearAmmoWarningState(BotEntry entry) {
        entry.setNoAmmo(false);
        entry.setAmmoWarnSent(false);
    }
}
