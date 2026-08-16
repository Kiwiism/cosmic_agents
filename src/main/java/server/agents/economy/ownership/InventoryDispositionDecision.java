package server.agents.economy.ownership;

import java.util.Objects;

public record InventoryDispositionDecision(InventoryItemRef item, int quantity, Disposition disposition,
                                           String reason, String legacyAction, String shadowAction,
                                           boolean shadowDisagreement) {
    public InventoryDispositionDecision {
        item = Objects.requireNonNull(item);
        disposition = Objects.requireNonNull(disposition);
        reason = Objects.requireNonNullElse(reason, "");
        legacyAction = Objects.requireNonNullElse(legacyAction, "NONE");
        shadowAction = Objects.requireNonNullElse(shadowAction, "NONE");
    }

    public enum Disposition {
        PROTECTED_UNREVIEWED,
        KEEP_REVIEWED,
        NPC_SALE_AUTHORIZED,
        PLAYER_SHOP_LISTING_RESERVED
    }
}
