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
        Map<Integer, Integer> killCounts,
        DeathOutcome death
) {
    public FarmSessionOutcome(String sessionId, String agentId, int mapId, Instant completedAt,
                              long experience, long mesos, List<ItemDrop> itemDrops,
                              List<FarmSessionPlan.ItemConsumption> consumedItems,
                              Map<Integer, Integer> killCounts) {
        this(sessionId, "explicit-work", agentId, mapId, completedAt, experience, mesos,
                itemDrops, consumedItems, killCounts, DeathOutcome.survived());
    }

    public FarmSessionOutcome {
        itemDrops = List.copyOf(itemDrops);
        consumedItems = List.copyOf(consumedItems);
        killCounts = Map.copyOf(killCounts);
        death = death == null ? DeathOutcome.survived() : death;
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

    public record DeathOutcome(boolean died, Instant occurredAt, long downtimeMillis,
                               double calibratedProbabilityPerHour) {
        public DeathOutcome {
            if (died && occurredAt == null) throw new IllegalArgumentException("death time is required");
            if (!died && occurredAt != null) throw new IllegalArgumentException("survival cannot have a death time");
            if (downtimeMillis < 0 || calibratedProbabilityPerHour < 0d
                    || calibratedProbabilityPerHour > 1d) throw new IllegalArgumentException();
        }

        public static DeathOutcome survived() {
            return new DeathOutcome(false, null, 0, 0d);
        }
    }
}
