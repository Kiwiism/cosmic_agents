package server.agents.economy.ownership;

import java.util.Objects;

/** Behavior proposal supplied by the existing agent policy; the facade remains the mutation authority. */
public record LegacyDispositionProposal(InventoryItemRef item, int quantity, Action action,
                                        String reason, String venue) {
    public LegacyDispositionProposal {
        item = Objects.requireNonNull(item);
        action = Objects.requireNonNull(action);
        reason = Objects.requireNonNullElse(reason, "");
        venue = Objects.requireNonNullElse(venue, "");
        if (quantity <= 0) throw new IllegalArgumentException("proposal quantity must be positive");
    }

    public enum Action { SELL_TO_NPC, LIST_IN_PLAYER_SHOP }
}
