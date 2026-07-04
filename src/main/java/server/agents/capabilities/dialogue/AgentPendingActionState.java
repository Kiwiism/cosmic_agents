package server.agents.capabilities.dialogue;

/**
 * Mutable pending chat action state for a live Agent.
 */
public final class AgentPendingActionState {
    private String pendingAction = null;
    private String pendingDropCategory = null;

    public String pendingAction() {
        return pendingAction;
    }

    public void setPendingAction(String pendingAction) {
        this.pendingAction = pendingAction;
    }

    public void clearPendingAction() {
        pendingAction = null;
    }

    public String pendingDropCategory() {
        return pendingDropCategory;
    }

    public void setPendingDropCategory(String pendingDropCategory) {
        this.pendingDropCategory = pendingDropCategory;
    }

    public void clearPendingDropCategory() {
        pendingDropCategory = null;
    }
}
