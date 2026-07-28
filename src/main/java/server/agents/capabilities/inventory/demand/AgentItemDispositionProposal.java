package server.agents.capabilities.inventory.demand;

import java.util.List;

/**
 * Read-only proposal. It deliberately carries evidence instead of a mutation callback so
 * deterministic policy, diagnostics, and a future LLM adapter share the same safe contract.
 */
public record AgentItemDispositionProposal(
        int itemId,
        String itemName,
        int ownedQuantity,
        int protectedQuantity,
        int proposedQuantity,
        Disposition disposition,
        int precedence,
        String target,
        String catalogRevision,
        List<String> evidence) {

    public AgentItemDispositionProposal {
        if (itemId <= 0 || ownedQuantity < 0 || protectedQuantity < 0
                || proposedQuantity < 0 || disposition == null
                || precedence < 1 || precedence > 10) {
            throw new IllegalArgumentException("valid item disposition proposal is required");
        }
        itemName = itemName == null ? "" : itemName;
        target = target == null ? "" : target;
        catalogRevision = catalogRevision == null ? "" : catalogRevision;
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public enum Disposition {
        KEEP_ACTIVE_QUEST,
        KEEP_COMMITTED_QUEST,
        KEEP_EXISTING_RESERVATION,
        KEEP_NEAR_TERM,
        KEEP_MID_TERM,
        KEEP_LONG_TERM,
        TRANSFER_TO_COHORT,
        STORE,
        SELL_SAFE_SURPLUS,
        HOLD_FOR_REVIEW
    }
}
