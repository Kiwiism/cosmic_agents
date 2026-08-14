package server.agents.economy.catalog;

import java.util.Map;
import java.util.Set;

public record ItemFact(int itemId, String name, int npcUnitSalePrice, Integer requiredLevel,
                       int slotMaximum, Set<ItemCategory> categories, Map<String, Integer> mechanics) {
    public ItemFact {
        if (itemId <= 0) throw new IllegalArgumentException("itemId must be positive");
        name = name == null ? "" : name;
        if (npcUnitSalePrice < 0) throw new IllegalArgumentException("NPC sale price cannot be negative");
        if (slotMaximum < 0) throw new IllegalArgumentException("slot maximum cannot be negative");
        categories = categories == null ? Set.of() : Set.copyOf(categories);
        mechanics = mechanics == null ? Map.of() : Map.copyOf(mechanics);
    }
}
