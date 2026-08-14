package server.agents.economy.decision;

import server.agents.economy.market.EconomicReason;

/** Chooses keep/NPC/stall from private beliefs and explicit opportunity costs. */
public final class ItemDispositionPolicy {
    public Decision decide(Input input) {
        if (input.neededQuantity() > 0) {
            return new Decision(Action.KEEP, 0, EconomicReason.QUEST_REQUIREMENT,
                    "need deficit=" + input.neededQuantity());
        }
        long netMarket = input.observedMarketUnitPrice() <= 0 ? 0
                : Math.max(0, input.observedMarketUnitPrice() - input.expectedTaxPerUnit()
                - input.listingOpportunityCostPerUnit());
        if (netMarket > input.npcUnitPrice() && input.saleProbability() > 0) {
            return new Decision(Action.LIST_AT_STALL, input.observedMarketUnitPrice(),
                    EconomicReason.OBSERVED_UNDERPRICING,
                    "observedNet=" + netMarket + " npcFloor=" + input.npcUnitPrice()
                            + " saleProbability=" + input.saleProbability());
        }
        return new Decision(Action.SELL_TO_NPC, input.npcUnitPrice(),
                input.inventoryPressure() ? EconomicReason.INVENTORY_PRESSURE
                        : EconomicReason.NPC_FLOOR_DISPOSITION,
                "marketNet=" + netMarket + " npcFloor=" + input.npcUnitPrice());
    }

    public enum Action { KEEP, SELL_TO_NPC, LIST_AT_STALL }
    public record Decision(Action action, long unitPrice, EconomicReason reason, String evidence) { }
    public record Input(int neededQuantity, long npcUnitPrice, long observedMarketUnitPrice,
                        long expectedTaxPerUnit, long listingOpportunityCostPerUnit,
                        double saleProbability, boolean inventoryPressure) {
        public Input {
            if (neededQuantity < 0 || npcUnitPrice < 0 || observedMarketUnitPrice < 0
                    || expectedTaxPerUnit < 0 || listingOpportunityCostPerUnit < 0
                    || saleProbability < 0 || saleProbability > 1) throw new IllegalArgumentException();
        }
    }
}
