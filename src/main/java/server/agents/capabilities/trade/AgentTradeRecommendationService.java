package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;

import java.util.List;
import java.util.function.BiFunction;

public final class AgentTradeRecommendationService {
    private AgentTradeRecommendationService() {
    }

    public static List<Item> recommendedItems(Character owner,
                                              Character agent,
                                              BiFunction<Character, Character, List<Item>> recommendationCollector) {
        return owner != null ? recommendationCollector.apply(owner, agent) : List.of();
    }
}
