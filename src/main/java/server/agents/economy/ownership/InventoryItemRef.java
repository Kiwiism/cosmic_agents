package server.agents.economy.ownership;

import java.util.Objects;

/** Stable identity for one physical inventory slot without inventing a game item identifier. */
public record InventoryItemRef(String inventoryType, short slot, int itemId, String fingerprint) {
    public InventoryItemRef {
        inventoryType = Objects.requireNonNull(inventoryType);
        fingerprint = Objects.requireNonNull(fingerprint);
        if (inventoryType.isBlank() || itemId <= 0 || fingerprint.isBlank())
            throw new IllegalArgumentException("inventory item identity is incomplete");
    }
}
