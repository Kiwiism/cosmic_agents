package server.agents.economy.ownership;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable inventory view. Revision covers all ordered item identities, quantities, and attributes. */
public record InventorySnapshot(int characterId, String revision, List<InventoryItemSnapshot> items) {
    public InventorySnapshot {
        if (characterId <= 0) throw new IllegalArgumentException("character id must be positive");
        revision = Objects.requireNonNull(revision);
        if (revision.isBlank()) throw new IllegalArgumentException("inventory revision is required");
        items = List.copyOf(items);
    }

    public Optional<InventoryItemSnapshot> find(String inventoryType, short slot, int itemId) {
        return items.stream().filter(item -> item.ref().inventoryType().equals(inventoryType)
                && item.ref().slot() == slot && item.ref().itemId() == itemId).findFirst();
    }
}
