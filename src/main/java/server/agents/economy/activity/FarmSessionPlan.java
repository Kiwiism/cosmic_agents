package server.agents.economy.activity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Explicit calibrated work input. The resolver never fabricates kills or resource costs. */
public record FarmSessionPlan(
        String sessionId,
        String agentId,
        int mapId,
        Instant startedAt,
        Duration duration,
        int dropRateMultiplier,
        List<MonsterWork> monsters,
        Set<Integer> activeQuestIds,
        List<ItemConsumption> consumedItems
) {
    public FarmSessionPlan {
        if (sessionId == null || sessionId.isBlank() || agentId == null || agentId.isBlank())
            throw new IllegalArgumentException("session and agent are required");
        if (mapId <= 0 || startedAt == null || duration == null || duration.isNegative()
                || duration.isZero() || dropRateMultiplier <= 0)
            throw new IllegalArgumentException("invalid farm session bounds");
        monsters = monsters == null ? List.of() : List.copyOf(monsters);
        activeQuestIds = activeQuestIds == null ? Set.of() : Set.copyOf(activeQuestIds);
        consumedItems = consumedItems == null ? List.of() : List.copyOf(consumedItems);
    }

    public record MonsterWork(int monsterId, int kills, int experiencePerKill) {
        public MonsterWork {
            if (monsterId <= 0 || kills < 0 || experiencePerKill < 0)
                throw new IllegalArgumentException("invalid monster work");
        }
    }

    public record ItemConsumption(int itemId, int quantity, String lotId) {
        public ItemConsumption {
            if (itemId <= 0 || quantity <= 0 || lotId == null || lotId.isBlank())
                throw new IllegalArgumentException("consumption provenance is required");
        }
    }
}
