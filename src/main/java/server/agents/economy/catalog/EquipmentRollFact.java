package server.agents.economy.catalog;

import java.util.Map;

public record EquipmentRollFact(int itemId, Map<String, Integer> stats) {
    public EquipmentRollFact {
        if (itemId <= 0 || stats == null) throw new IllegalArgumentException();
        stats = Map.copyOf(stats);
    }
}
