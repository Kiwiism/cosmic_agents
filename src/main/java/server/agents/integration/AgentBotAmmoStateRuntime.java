package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned adapter for ammo sharing state.
 */
public final class AgentBotAmmoStateRuntime {
    private AgentBotAmmoStateRuntime() {
    }

    public static boolean ammoShareRequested(BotEntry entry) {
        return entry.ammoSupplyState().shareRequested();
    }

    public static void setAmmoShareRequested(BotEntry entry, boolean requested) {
        entry.ammoSupplyState().setShareRequested(requested);
    }

    public static void clearAmmoShareRequested(BotEntry entry) {
        entry.ammoSupplyState().setShareRequested(false);
    }

    public static boolean noAmmo(BotEntry entry) {
        return entry.ammoSupplyState().noAmmo();
    }

    public static void setNoAmmo(BotEntry entry, boolean noAmmo) {
        entry.ammoSupplyState().setNoAmmo(noAmmo);
    }

    public static boolean ammoWarnSent(BotEntry entry) {
        return entry.ammoSupplyState().warnSent();
    }

    public static void setAmmoWarnSent(BotEntry entry, boolean ammoWarnSent) {
        entry.ammoSupplyState().setWarnSent(ammoWarnSent);
    }

    public static void clearAmmoWarningState(BotEntry entry) {
        entry.ammoSupplyState().clearWarningState();
    }
}
