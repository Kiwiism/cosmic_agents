package server.agents.capabilities.inventory.demand;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Deterministic precedence policy for quest-aware inventory decisions. */
public final class AgentItemDispositionProposalService {

    public AgentItemDispositionProposal propose(
            AgentQuestItemDemandForecast.ItemForecast forecast,
            int existingReservedQuantity,
            Map<String, Integer> cohortShortages,
            boolean storageAvailable) {
        return propose(forecast, existingReservedQuantity, cohortShortages,
                storageAvailable, "");
    }

    public AgentItemDispositionProposal propose(
            AgentQuestItemDemandForecast.ItemForecast forecast,
            int existingReservedQuantity,
            Map<String, Integer> cohortShortages,
            boolean storageAvailable,
            String catalogRevision) {
        if (forecast == null) {
            throw new IllegalArgumentException("item forecast is required");
        }
        int active = forecast.demand(AgentQuestDemandCategory.ACTIVE);
        int committed = forecast.demand(AgentQuestDemandCategory.COMMITTED);
        int near = forecast.demand(AgentQuestDemandCategory.WITHIN_5_LEVELS);
        int mid = forecast.demand(AgentQuestDemandCategory.WITHIN_15_LEVELS);
        int distant = forecast.demand(AgentQuestDemandCategory.WITHIN_25_LEVELS);
        int owned = forecast.ownedQuantity();
        int questProtected = active + committed + near + mid + distant;
        int protectedQuantity = Math.min(owned, Math.max(existingReservedQuantity, questProtected));
        int surplus = Math.max(0, owned - protectedQuantity);
        List<String> evidence = new ArrayList<>();
        evidence.add("active=" + active);
        evidence.add("committed=" + committed);
        evidence.add("within5=" + near);
        evidence.add("within15=" + mid);
        evidence.add("within25=" + distant);
        evidence.add("ledgerReserved=" + existingReservedQuantity);

        if (surplus == 0) {
            if (active > 0) {
                return proposal(forecast, protectedQuantity, Math.min(owned, active),
                        AgentItemDispositionProposal.Disposition.KEEP_ACTIVE_QUEST, 1, "",
                        catalogRevision, evidence);
            }
            if (committed > 0) {
                return proposal(forecast, protectedQuantity, Math.min(owned, committed),
                        AgentItemDispositionProposal.Disposition.KEEP_COMMITTED_QUEST, 2, "",
                        catalogRevision, evidence);
            }
            if (existingReservedQuantity > 0) {
                return proposal(forecast, protectedQuantity,
                        Math.min(owned, existingReservedQuantity),
                        AgentItemDispositionProposal.Disposition.KEEP_EXISTING_RESERVATION,
                        3, "", catalogRevision, evidence);
            }
            if (near > 0) {
                return proposal(forecast, protectedQuantity, Math.min(owned, near),
                        AgentItemDispositionProposal.Disposition.KEEP_NEAR_TERM, 4, "",
                        catalogRevision, evidence);
            }
            if (mid > 0) {
                return proposal(forecast, protectedQuantity, Math.min(owned, mid),
                        AgentItemDispositionProposal.Disposition.KEEP_MID_TERM, 5, "",
                        catalogRevision, evidence);
            }
            if (distant > 0) {
                return proposal(forecast, protectedQuantity, Math.min(owned, distant),
                        AgentItemDispositionProposal.Disposition.KEEP_LONG_TERM, 6, "",
                        catalogRevision, evidence);
            }
        }

        Map.Entry<String, Integer> recipient = cohortShortages == null ? null
                : cohortShortages.entrySet().stream()
                .filter(row -> row.getKey() != null && !row.getKey().isBlank()
                        && row.getValue() != null && row.getValue() > 0)
                .sorted(Map.Entry.comparingByKey())
                .findFirst().orElse(null);
        if (surplus > 0 && recipient != null) {
            return proposal(forecast, protectedQuantity,
                    Math.min(surplus, recipient.getValue()),
                    AgentItemDispositionProposal.Disposition.TRANSFER_TO_COHORT,
                    7, recipient.getKey(), catalogRevision, evidence);
        }
        if (surplus > 0 && storageAvailable) {
            return proposal(forecast, protectedQuantity, surplus,
                    AgentItemDispositionProposal.Disposition.STORE, 8, "storage",
                    catalogRevision, evidence);
        }
        if (surplus > 0) {
            return proposal(forecast, protectedQuantity, surplus,
                    AgentItemDispositionProposal.Disposition.SELL_SAFE_SURPLUS, 9, "npc-shop",
                    catalogRevision, evidence);
        }
        return proposal(forecast, protectedQuantity, 0,
                AgentItemDispositionProposal.Disposition.HOLD_FOR_REVIEW,
                10, "", catalogRevision, evidence);
    }

    private static AgentItemDispositionProposal proposal(
            AgentQuestItemDemandForecast.ItemForecast forecast,
            int protectedQuantity,
            int proposedQuantity,
            AgentItemDispositionProposal.Disposition disposition,
            int precedence,
            String target,
            String catalogRevision,
            List<String> evidence) {
        return new AgentItemDispositionProposal(
                forecast.itemId(), forecast.itemName(), forecast.ownedQuantity(),
                protectedQuantity, proposedQuantity, disposition, precedence, target,
                catalogRevision, evidence);
    }
}
