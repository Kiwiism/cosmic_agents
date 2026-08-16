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
        List<UncollectedDrop> uncollectedDrops,
        List<FarmSessionPlan.ItemConsumption> consumedItems,
        Map<Integer, Integer> killCounts,
        DeathOutcome death
) {
    public FarmSessionOutcome(String sessionId, String agentId, int mapId, Instant completedAt,
                              long experience, long mesos, List<ItemDrop> itemDrops,
                              List<FarmSessionPlan.ItemConsumption> consumedItems,
                              Map<Integer, Integer> killCounts) {
        this(sessionId, "explicit-work", agentId, mapId, completedAt, experience, mesos,
                itemDrops, List.of(), consumedItems, killCounts, DeathOutcome.survived());
    }

    public FarmSessionOutcome(String sessionId, String calibrationId, String agentId, int mapId,
                              Instant completedAt, long experience, long mesos, List<ItemDrop> itemDrops,
                              List<FarmSessionPlan.ItemConsumption> consumedItems,
                              Map<Integer, Integer> killCounts, DeathOutcome death) {
        this(sessionId, calibrationId, agentId, mapId, completedAt, experience, mesos,
                itemDrops, List.of(), consumedItems, killCounts, death);
    }

    public FarmSessionOutcome {
        itemDrops = List.copyOf(itemDrops);
        uncollectedDrops = List.copyOf(uncollectedDrops);
        consumedItems = List.copyOf(consumedItems);
        killCounts = Map.copyOf(killCounts);
        death = death == null ? DeathOutcome.survived() : death;
    }

    /** A real drop roll that could not enter the character's inventory under Cosmic rules. */
    public record UncollectedDrop(ItemDrop drop, String reason) {
        public UncollectedDrop {
            if (drop == null || reason == null || reason.isBlank()) throw new IllegalArgumentException();
        }
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
