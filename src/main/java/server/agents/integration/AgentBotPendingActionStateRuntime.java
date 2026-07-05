package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned pending-action state adapter.
 */
public final class AgentBotPendingActionStateRuntime {
    private AgentBotPendingActionStateRuntime() {
    }

    public static boolean hasPendingAction(AgentRuntimeEntry entry) {
        return entry.pendingActionState().pendingAction() != null;
    }

    public static String pendingAction(AgentRuntimeEntry entry) {
        return entry.pendingActionState().pendingAction();
    }

    public static void setPendingAction(AgentRuntimeEntry entry, String pendingAction) {
        entry.pendingActionState().setPendingAction(pendingAction);
    }

    public static void clearPendingAction(AgentRuntimeEntry entry) {
        entry.pendingActionState().clearPendingAction();
    }

    public static String pendingDropCategory(AgentRuntimeEntry entry) {
        return entry.pendingActionState().pendingDropCategory();
    }

    public static void setPendingDropCategory(AgentRuntimeEntry entry, String pendingDropCategory) {
        entry.pendingActionState().setPendingDropCategory(pendingDropCategory);
    }

    public static void clearPendingDropCategory(AgentRuntimeEntry entry) {
        entry.pendingActionState().clearPendingDropCategory();
    }
}
