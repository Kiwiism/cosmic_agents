package server.agents.economy.decision;

import server.agents.economy.market.EconomicReason;

public record DemandSignal(int itemId, long demandedQuantity, int interestedAgents,
                           double meanUrgency, EconomicReason reason, String evidence) {
    public DemandSignal {
        if (itemId <= 0 || demandedQuantity < 0 || interestedAgents < 0 || meanUrgency < 0
                || meanUrgency > 1 || reason == null || evidence == null) {
            throw new IllegalArgumentException("invalid demand signal");
        }
    }
}
