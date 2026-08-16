package server.agents.economy.ownership;

import server.agents.economy.catalog.EconomyCatalog;
import server.agents.economy.catalog.ItemCategory;

import java.util.Objects;

/** Advisory-only safety evaluator. Disagreements are journaled and never change behavior in this phase. */
public final class ShadowEconomyEvaluator {
    private final EconomyCatalog catalog;

    public ShadowEconomyEvaluator(EconomyCatalog catalog) { this.catalog = Objects.requireNonNull(catalog); }

    public String evaluate(InventoryItemSnapshot item, LegacyDispositionProposal proposal) {
        if (proposal.action() != LegacyDispositionProposal.Action.SELL_TO_NPC)
            return proposal.action().name();
        boolean marketSensitive = catalog.item(item.ref().itemId()).stream()
                .flatMap(fact -> fact.categories().stream())
                .anyMatch(category -> category == ItemCategory.EQUIPMENT
                        || category == ItemCategory.EQUIP_SCROLL || category == ItemCategory.CHAIR
                        || category == ItemCategory.QUEST_ITEM);
        return marketSensitive ? "KEEP_FOR_MARKET_REVIEW" : proposal.action().name();
    }
}
