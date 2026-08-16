package server.agents.economy.persistence;

import java.util.List;
import java.util.Map;

public record EconomyBootstrapSnapshot(int characterId, long mesos, int level, long experience,
                                       List<Holding> holdings) {
    public EconomyBootstrapSnapshot { holdings = List.copyOf(holdings); }

    public record Holding(int itemId, long quantity, String inventoryType, String fingerprint,
                          Map<String, Object> attributes) {
        public Holding { attributes = Map.copyOf(attributes); }
        public boolean equipment() { return "EQUIP".equals(inventoryType) || "EQUIPPED".equals(inventoryType); }
    }
}
