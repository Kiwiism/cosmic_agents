package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned pending-action state adapter. Pending chat actions and drop
 * categories are still backed by BotEntry during reconstruction, but callers
 * should depend on this narrow state boundary.
 */
public final class AgentBotPendingActionStateRuntime {
    private AgentBotPendingActionStateRuntime() {
    }

    public static boolean hasPendingAction(BotEntry entry) {
        return entry.pendingAction() != null;
    }

    public static String pendingAction(BotEntry entry) {
        return entry.pendingAction();
    }

    public static void setPendingAction(BotEntry entry, String pendingAction) {
        entry.setPendingAction(pendingAction);
    }

    public static void clearPendingAction(BotEntry entry) {
        entry.clearPendingAction();
    }

    public static String pendingDropCategory(BotEntry entry) {
        return entry.pendingDropCategory();
    }

    public static void setPendingDropCategory(BotEntry entry, String pendingDropCategory) {
        entry.setPendingDropCategory(pendingDropCategory);
    }

    public static void clearPendingDropCategory(BotEntry entry) {
        entry.clearPendingDropCategory();
    }
}
