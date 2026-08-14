package server.agents.economy.decision;

import server.agents.economy.market.EconomicReason;

import java.time.Instant;

public record AgentNeed(int itemId, int currentQuantity, int targetQuantity, double urgency,
                        EconomicReason reason, Instant neededBy) {
    public AgentNeed {
        if (itemId <= 0 || currentQuantity < 0 || targetQuantity <= 0 || urgency < 0
                || urgency > 1 || reason == null) throw new IllegalArgumentException("invalid agent need");
    }

    public int deficit() { return Math.max(0, targetQuantity - currentQuantity); }
}
