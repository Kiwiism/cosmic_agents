package server.agents.economy.activity;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record FarmSessionOutcome(
        String sessionId,
        String calibrationId,
        String agentId,
        int mapId,
        Instant completedAt,
        long experience,
        long mesos,
        List<ItemDrop> itemDrops,
        List<FarmSessionPlan.ItemConsumption> consumedItems,
        Map<Integer, Integer> killCounts
) {
    public FarmSessionOutcome(String sessionId, String agentId, int mapId, Instant completedAt,
                              long experience, long mesos, List<ItemDrop> itemDrops,
                              List<FarmSessionPlan.ItemConsumption> consumedItems,
                              Map<Integer, Integer> killCounts) {
        this(sessionId, "explicit-work", agentId, mapId, completedAt, experience, mesos,
                itemDrops, consumedItems, killCounts);
    }

    public FarmSessionOutcome {
        itemDrops = List.copyOf(itemDrops);
        consumedItems = List.copyOf(consumedItems);
        killCounts = Map.copyOf(killCounts);
    }

    public record ItemDrop(String lotId, int monsterId, int killOrdinal, int itemId,
                           int quantity, int questId, int baseChance, int effectiveChance,
                           Map<String, Integer> equipmentStats) {
        public ItemDrop(String lotId, int monsterId, int killOrdinal, int itemId,
                        int quantity, int questId, int baseChance, int effectiveChance) {
            this(lotId, monsterId, killOrdinal, itemId, quantity, questId, baseChance,
                    effectiveChance, Map.of());
        }
        public ItemDrop { equipmentStats = Map.copyOf(equipmentStats); }
    }
}
