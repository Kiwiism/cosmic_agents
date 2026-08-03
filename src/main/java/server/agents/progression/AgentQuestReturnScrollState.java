package server.agents.progression;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Per-trip guard that keeps optional return-scroll shopping from reopening forever. */
final class AgentQuestReturnScrollState {
    static final AgentCapabilityStateKey<AgentQuestReturnScrollState> STATE_KEY =
            new AgentCapabilityStateKey<>("progression.quest-return-scroll",
                    AgentQuestReturnScrollState.class, AgentQuestReturnScrollState::new);

    private String tripKey = "";
    private boolean purchaseAttempted;
    private boolean returnEligible;
    private int returnScrollItemId;
    private int completionMapId;

    synchronized void begin(String nextTripKey) {
        String normalized = nextTripKey == null ? "" : nextTripKey;
        if (tripKey.equals(normalized)) {
            return;
        }
        tripKey = normalized;
        purchaseAttempted = false;
        returnEligible = false;
        returnScrollItemId = 0;
        completionMapId = 0;
    }

    synchronized boolean purchaseAttempted() {
        return purchaseAttempted;
    }

    synchronized void markPurchaseAttempted() {
        purchaseAttempted = true;
    }

    synchronized void markReturnEligible(int itemId, int destinationMapId) {
        returnEligible = true;
        returnScrollItemId = itemId;
        completionMapId = destinationMapId;
    }

    synchronized boolean returnEligible(int destinationMapId) {
        return returnEligible && completionMapId == destinationMapId;
    }

    synchronized int returnScrollItemId() {
        return returnScrollItemId;
    }

    synchronized void clear() {
        tripKey = "";
        purchaseAttempted = false;
        returnEligible = false;
        returnScrollItemId = 0;
        completionMapId = 0;
    }
}
