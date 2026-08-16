package server.agents.economy.ownership;

import java.util.Map;
import java.util.Objects;

public record InventoryItemSnapshot(InventoryItemRef ref, int quantity, Map<String, Object> attributes) {
    public InventoryItemSnapshot {
        ref = Objects.requireNonNull(ref);
        if (quantity <= 0) throw new IllegalArgumentException("inventory quantity must be positive");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
