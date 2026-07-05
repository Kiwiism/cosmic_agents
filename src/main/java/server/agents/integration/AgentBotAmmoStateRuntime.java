package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for ammo sharing state.
 */
public final class AgentBotAmmoStateRuntime {
    private AgentBotAmmoStateRuntime() {
    }

    public static boolean ammoShareRequested(AgentRuntimeEntry entry) {
        return entry.ammoSupplyState().shareRequested();
    }

    public static void setAmmoShareRequested(AgentRuntimeEntry entry, boolean requested) {
        entry.ammoSupplyState().setShareRequested(requested);
    }

    public static void clearAmmoShareRequested(AgentRuntimeEntry entry) {
        entry.ammoSupplyState().setShareRequested(false);
    }

    public static boolean noAmmo(AgentRuntimeEntry entry) {
        return entry.ammoSupplyState().noAmmo();
    }

    public static void setNoAmmo(AgentRuntimeEntry entry, boolean noAmmo) {
        entry.ammoSupplyState().setNoAmmo(noAmmo);
    }

    public static boolean ammoWarnSent(AgentRuntimeEntry entry) {
        return entry.ammoSupplyState().warnSent();
    }

    public static void setAmmoWarnSent(AgentRuntimeEntry entry, boolean ammoWarnSent) {
        entry.ammoSupplyState().setWarnSent(ammoWarnSent);
    }

    public static void clearAmmoWarningState(AgentRuntimeEntry entry) {
        entry.ammoSupplyState().clearWarningState();
    }
}
