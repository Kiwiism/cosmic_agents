package server.agents.economy.ownership;

import java.util.*;

/** Converts current behavior proposals into explicit, auditable ownership decisions. */
public final class LegacyDispositionEvaluator {
    public List<InventoryDispositionDecision> evaluate(InventorySnapshot snapshot,
                                                        List<LegacyDispositionProposal> proposals,
                                                        ShadowEconomyEvaluator shadow) {
        Map<InventoryItemRef, LegacyDispositionProposal> proposed = new HashMap<>();
        proposals.forEach(value -> proposed.put(value.item(), value));
        List<InventoryDispositionDecision> decisions = new ArrayList<>();
        for (InventoryItemSnapshot item : snapshot.items()) {
            LegacyDispositionProposal proposal = proposed.get(item.ref());
            if (proposal == null) {
                decisions.add(new InventoryDispositionDecision(item.ref(), item.quantity(),
                        InventoryDispositionDecision.Disposition.KEEP_REVIEWED,
                        "No legacy disposal proposal", "NONE", "KEEP", false));
                continue;
            }
            String shadowAction = shadow.evaluate(item, proposal);
            String legacyAction = proposal.action().name();
            decisions.add(new InventoryDispositionDecision(item.ref(),
                    Math.min(item.quantity(), proposal.quantity()),
                    proposal.action() == LegacyDispositionProposal.Action.SELL_TO_NPC
                            ? InventoryDispositionDecision.Disposition.NPC_SALE_AUTHORIZED
                            : InventoryDispositionDecision.Disposition.PLAYER_SHOP_LISTING_RESERVED,
                    proposal.reason(), legacyAction, shadowAction, !legacyAction.equals(shadowAction)));
        }
        return List.copyOf(decisions);
    }
}
