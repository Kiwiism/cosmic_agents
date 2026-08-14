package server.agents.economy.social;

import java.util.Map;

public record TradeOffer(long mesos, Map<Integer, Integer> items) {
    public TradeOffer {
        if (mesos < 0) throw new IllegalArgumentException("mesos cannot be negative");
        items = items == null ? Map.of() : Map.copyOf(items);
        if (items.entrySet().stream().anyMatch(entry -> entry.getKey() <= 0 || entry.getValue() <= 0))
            throw new IllegalArgumentException("offered items must be positive");
        if (mesos == 0 && items.isEmpty()) throw new IllegalArgumentException("offer cannot be empty");
    }
}
