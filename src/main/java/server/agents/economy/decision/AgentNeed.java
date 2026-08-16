package server.agents.economy.decision;

import server.agents.economy.market.EconomicReason;

import java.time.Instant;
import java.util.Set;

public record AgentNeed(int itemId, int currentQuantity, int targetQuantity, double urgency,
                        EconomicReason reason, Instant neededBy, long maximumWillingnessToPay,
                        Set<Integer> substitutes, Set<Integer> complements, String evidence) {
    public AgentNeed(int itemId, int currentQuantity, int targetQuantity, double urgency,
                     EconomicReason reason, Instant neededBy) {
        this(itemId, currentQuantity, targetQuantity, urgency, reason, neededBy, 0,
                Set.of(), Set.of(), "");
    }

    public AgentNeed {
        if (itemId <= 0 || currentQuantity < 0 || targetQuantity <= 0 || urgency < 0
                || urgency > 1 || reason == null || maximumWillingnessToPay < 0)
            throw new IllegalArgumentException("invalid agent need");
        substitutes = substitutes == null ? Set.of() : Set.copyOf(substitutes);
        complements = complements == null ? Set.of() : Set.copyOf(complements);
        evidence = evidence == null ? "" : evidence;
    }

    public int deficit() { return Math.max(0, targetQuantity - currentQuantity); }
}
