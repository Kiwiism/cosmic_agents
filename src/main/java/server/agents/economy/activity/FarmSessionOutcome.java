package server.agents.economy.activity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record FarmSessionOutcome(
        String sessionId,
        String agentId,
        int mapId,
        Instant completedAt,
        long experience,
        long mesos,
        List<ItemDrop> itemDrops,
        List<FarmSessionPlan.ItemConsumption> consumedItems,
        Map<Integer, Integer> killCounts
) {
    public FarmSessionOutcome {
        itemDrops = List.copyOf(itemDrops);
        consumedItems = List.copyOf(consumedItems);
        killCounts = Map.copyOf(killCounts);
    }

    public record ItemDrop(String lotId, int monsterId, int killOrdinal, int itemId,
                           int quantity, int questId, int baseChance, int effectiveChance) { }
}
