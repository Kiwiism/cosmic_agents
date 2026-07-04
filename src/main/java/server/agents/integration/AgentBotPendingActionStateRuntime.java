package server.agents.integration;

import server.bots.BotEntry;

/**
 * Agent-owned pending-action state adapter.
 */
public final class AgentBotPendingActionStateRuntime {
    private AgentBotPendingActionStateRuntime() {
    }

    public static boolean hasPendingAction(BotEntry entry) {
        return entry.pendingActionState().pendingAction() != null;
    }

    public static String pendingAction(BotEntry entry) {
        return entry.pendingActionState().pendingAction();
    }

    public static void setPendingAction(BotEntry entry, String pendingAction) {
        entry.pendingActionState().setPendingAction(pendingAction);
    }

    public static void clearPendingAction(BotEntry entry) {
        entry.pendingActionState().clearPendingAction();
    }

    public static String pendingDropCategory(BotEntry entry) {
        return entry.pendingActionState().pendingDropCategory();
    }

    public static void setPendingDropCategory(BotEntry entry, String pendingDropCategory) {
        entry.pendingActionState().setPendingDropCategory(pendingDropCategory);
    }

    public static void clearPendingDropCategory(BotEntry entry) {
        entry.pendingActionState().clearPendingDropCategory();
    }
}
