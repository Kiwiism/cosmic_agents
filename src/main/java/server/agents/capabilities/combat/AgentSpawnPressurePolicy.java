package server.agents.capabilities.combat;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Selects non-objective monsters that may be cleared when they are consuming a
 * disproportionate share of a mixed-spawn map. The authored spawn-point ratio
 * remains the baseline, so the policy adapts to every map without mob-specific
 * thresholds.
 */
public final class AgentSpawnPressurePolicy {
    private AgentSpawnPressurePolicy() {
    }

    public static Set<Integer> selectFallbackMobIds(
            Map<Integer, Integer> configuredSpawnCounts,
            Map<Integer, Integer> liveMonsterCounts,
            Set<Integer> preferredMobIds,
            Set<Integer> fallbackMobIds,
            int minimumTargetSharePercent) {
        if (configuredSpawnCounts == null || configuredSpawnCounts.isEmpty()
                || liveMonsterCounts == null || liveMonsterCounts.isEmpty()
                || preferredMobIds == null || preferredMobIds.isEmpty()
                || fallbackMobIds == null || fallbackMobIds.isEmpty()) {
            return Set.of();
        }

        long configuredTotal = positiveTotal(configuredSpawnCounts);
        long configuredPreferred = totalFor(configuredSpawnCounts, preferredMobIds);
        long liveTotal = positiveTotal(liveMonsterCounts);
        long livePreferred = totalFor(liveMonsterCounts, preferredMobIds);
        if (configuredTotal == 0 || configuredPreferred == 0 || liveTotal == 0) {
            return Set.of();
        }

        int boundedMinimumShare = Math.max(1, Math.min(100, minimumTargetSharePercent));
        boolean preferredUnderrepresented = livePreferred == 0
                || livePreferred * configuredTotal * 100L
                < liveTotal * configuredPreferred * boundedMinimumShare;
        if (!preferredUnderrepresented) {
            return Set.of();
        }

        Set<Integer> selected = new LinkedHashSet<>();
        for (int mobId : fallbackMobIds) {
            long configured = Math.max(0, configuredSpawnCounts.getOrDefault(mobId, 0));
            long live = Math.max(0, liveMonsterCounts.getOrDefault(mobId, 0));
            if (configured > 0 && live > 0
                    && live * configuredTotal > liveTotal * configured) {
                selected.add(mobId);
            }
        }

        // With a small live sample no individual filler species may exceed its
        // own authored share even though every preferred spawn is absent. Clear
        // one of the configured live fallback species so respawn gets a chance.
        if (selected.isEmpty() && livePreferred == 0) {
            fallbackMobIds.stream()
                    .filter(configuredSpawnCounts::containsKey)
                    .filter(mobId -> liveMonsterCounts.getOrDefault(mobId, 0) > 0)
                    .forEach(selected::add);
        }
        return Set.copyOf(selected);
    }

    private static long positiveTotal(Map<Integer, Integer> counts) {
        return counts.values().stream().mapToLong(value -> Math.max(0, value)).sum();
    }

    private static long totalFor(Map<Integer, Integer> counts, Set<Integer> mobIds) {
        return mobIds.stream().mapToLong(mobId -> Math.max(0, counts.getOrDefault(mobId, 0))).sum();
    }
}
