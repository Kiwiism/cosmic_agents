package server.agents.economy.ownership;

import java.time.Instant;
import java.util.UUID;

public interface EconomyOwnershipJournal {
    void appendReview(InventoryReview review);
    void markAuthorizationConsumed(UUID authorizationId, Instant at);
    void appendGuardEvent(UUID runId, String agentId, int characterId, Instant at,
                          String action, InventoryItemRef item, int quantity,
                          boolean allowed, String reason, UUID authorizationId);

    static EconomyOwnershipJournal noOp() {
        return new EconomyOwnershipJournal() {
            @Override public void appendReview(InventoryReview review) { }
            @Override public void markAuthorizationConsumed(UUID id, Instant at) { }
            @Override public void appendGuardEvent(UUID runId, String agentId, int characterId, Instant at,
                                                   String action, InventoryItemRef item, int quantity,
                                                   boolean allowed, String reason, UUID authorizationId) { }
        };
    }
}
