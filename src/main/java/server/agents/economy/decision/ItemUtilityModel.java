package server.agents.economy.decision;

import server.agents.economy.catalog.ItemFact;

import java.util.Map;

/** Job-specific utility over WZ mechanical stats; avoids item-id price rules. */
public final class ItemUtilityModel {
    public double utility(ItemFact item, Map<String, Double> statWeights) {
        double result = 0;
        for (Map.Entry<String, Integer> mechanic : item.mechanics().entrySet()) {
            result += mechanic.getValue() * statWeights.getOrDefault(mechanic.getKey(), 0d);
        }
        return result;
    }

    public double expectedScrollUtility(ItemFact scroll, Map<String, Double> statWeights,
                                        double targetReplacementCost, double remainingSlotValue) {
        double success = probability(scroll.mechanics().getOrDefault("success", 0));
        double curse = probability(scroll.mechanics().getOrDefault("cursed", 0));
        double statGain = utility(scroll, statWeights);
        return success * statGain - (1 - success) * remainingSlotValue
                - curse * targetReplacementCost;
    }

    private static double probability(int percent) {
        return Math.max(0, Math.min(100, percent)) / 100d;
    }
}
