package server.agents.economy.ownership;

import java.time.Instant;
import java.util.List;

/** Minimal ownership contract used by current and future autonomous agent implementations. */
public interface AgentEconomyFacade {
    InventoryReview protectAtFreeMarketEntry(String agentId, InventorySnapshot snapshot, Instant logicalAt);
    InventoryReview appraise(String agentId, InventorySnapshot snapshot,
                             List<LegacyDispositionProposal> proposals, Instant logicalAt);
    NpcSalePermit claimNpcSale(String agentId, InventorySnapshot current, InventoryItemRef item,
                               int quantity, String venue, Instant logicalAt);

    record NpcSalePermit(boolean allowed, String reason, java.util.UUID authorizationId) {
        public static NpcSalePermit denied(String reason) { return new NpcSalePermit(false, reason, null); }
    }
}
