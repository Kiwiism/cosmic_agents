package server.agents.field;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Pure capacity model shared by generated field catalogs and live farming cells. */
public final class AgentFieldCapacityEstimator {
    public static final int AGENT_SPACING_PX = 500;
    public static final int SPAWN_ENTRIES_PER_AGENT = 4;
    public static final int MINIMUM_ACTIVE_PERCENT = 40;
    public static final int PRODUCTIVE_PLATFORM_SPAWNS = 3;
    public static final int FRAGMENTATION_PENALTY_PERCENT = 15;

    private AgentFieldCapacityEstimator() {
    }

    public static int platformCapacity(int populatedWidth, int spawnEntries) {
        int widthCapacity = divideRoundUp(Math.max(1, populatedWidth), AGENT_SPACING_PX);
        int spawnCapacity = divideRoundUp(Math.max(1, spawnEntries), SPAWN_ENTRIES_PER_AGENT);
        return Math.max(1, Math.min(widthCapacity, spawnCapacity));
    }

    public static AgentFieldCapacityCatalog.MapCapacity estimate(
            MapEvidence evidence,
            CapacityOverride override) {
        if (evidence == null || evidence.mapId() <= 0 || evidence.mapName() == null
                || evidence.mapName().isBlank() || evidence.spawnPoints() == null
                || evidence.spawnPoints().isEmpty()) {
            throw new IllegalArgumentException("spawn-bearing map evidence is required");
        }
        Map<Integer, List<SpawnEvidence>> byComponent = new LinkedHashMap<>();
        evidence.spawnPoints().stream()
                .sorted(Comparator.comparingInt(SpawnEvidence::componentId)
                        .thenComparingInt(SpawnEvidence::x)
                        .thenComparingInt(SpawnEvidence::y))
                .forEach(spawn -> byComponent.computeIfAbsent(
                        spawn.componentId(), ignored -> new ArrayList<>()).add(spawn));

        ArrayList<AgentFieldCapacityCatalog.PlatformCapacity> platforms = new ArrayList<>();
        int rawCapacity = 0;
        int productivePlatforms = 0;
        int sparsePlatforms = 0;
        for (Map.Entry<Integer, List<SpawnEvidence>> component : byComponent.entrySet()) {
            List<SpawnEvidence> spawns = component.getValue();
            int minX = spawns.stream().mapToInt(SpawnEvidence::x).min().orElse(0);
            int maxX = spawns.stream().mapToInt(SpawnEvidence::x).max().orElse(minX);
            int minY = spawns.stream().mapToInt(SpawnEvidence::y).min().orElse(0);
            int maxY = spawns.stream().mapToInt(SpawnEvidence::y).max().orElse(minY);
            int populatedWidth = Math.max(1, maxX - minX);
            int spawnEntries = spawns.size();
            int widthCapacity = divideRoundUp(populatedWidth, AGENT_SPACING_PX);
            int spawnCapacity = divideRoundUp(spawnEntries, SPAWN_ENTRIES_PER_AGENT);
            int capacity = platformCapacity(populatedWidth, spawnEntries);
            boolean sparse = spawnEntries < PRODUCTIVE_PLATFORM_SPAWNS;
            if (sparse) {
                sparsePlatforms++;
            } else {
                productivePlatforms++;
            }
            rawCapacity += capacity;
            ArrayList<String> constraints = new ArrayList<>();
            if (widthCapacity <= spawnCapacity) {
                constraints.add("horizontal-spacing");
            }
            if (spawnCapacity <= widthCapacity) {
                constraints.add("spawn-pressure");
            }
            if (sparse) {
                constraints.add("sparse-isolated-platform");
            }
            platforms.add(new AgentFieldCapacityCatalog.PlatformCapacity(
                    "component-" + component.getKey(), List.of(component.getKey()),
                    spawnEntries, minX, maxX, minY, maxY, populatedWidth,
                    widthCapacity, spawnCapacity, capacity, sparse,
                    List.copyOf(constraints)));
        }

        int totalSpawns = Math.max(evidence.totalSpawnEntries(), evidence.spawnPoints().size());
        int spawnBudget = divideRoundUp(totalSpawns, SPAWN_ENTRIES_PER_AGENT);
        int constrainedCapacity = Math.max(1, Math.min(rawCapacity, spawnBudget));
        boolean fragmented = "high".equalsIgnoreCase(evidence.complexity())
                && platforms.size() >= 6
                && sparsePlatforms * 2 >= platforms.size()
                && evidence.climbableCount() > 0;
        int accessPenalty = fragmented
                ? Math.max(1, divideRoundUp(
                        constrainedCapacity * FRAGMENTATION_PENALTY_PERCENT, 100))
                : 0;
        int estimatedMaximum = Math.max(1, constrainedCapacity - accessPenalty);
        int recommendedMinimum = Math.max(1, Math.min(
                Math.max(1, productivePlatforms), divideRoundUp(estimatedMaximum, 2)));
        int recommendedMaximum = Math.max(recommendedMinimum,
                divideRoundUp(estimatedMaximum * 3, 4));

        String source = "generated";
        String confidence = fragmented || "high".equalsIgnoreCase(evidence.complexity())
                ? "medium" : "high";
        ArrayList<String> adjustments = new ArrayList<>();
        if (rawCapacity > spawnBudget) {
            adjustments.add("map spawn budget capped " + rawCapacity + " to " + spawnBudget);
        }
        if (accessPenalty > 0) {
            adjustments.add("fragmented high-complexity access reduced capacity by " + accessPenalty);
        }
        if (override != null) {
            recommendedMinimum = override.recommendedMinimum() == null
                    ? recommendedMinimum : override.recommendedMinimum();
            recommendedMaximum = override.recommendedMaximum() == null
                    ? recommendedMaximum : override.recommendedMaximum();
            estimatedMaximum = override.maximumAgents() == null
                    ? estimatedMaximum : override.maximumAgents();
            if (recommendedMinimum < 1 || recommendedMinimum > recommendedMaximum
                    || recommendedMaximum > estimatedMaximum) {
                throw new IllegalArgumentException("invalid capacity override for map " + evidence.mapId());
            }
            source = "manual-override";
            confidence = override.confidence() == null || override.confidence().isBlank()
                    ? "high" : override.confidence();
            adjustments.add(override.reason());
        }
        int minimumActiveFloor = divideRoundUp(
                estimatedMaximum * MINIMUM_ACTIVE_PERCENT, 100);
        if (recommendedMinimum < minimumActiveFloor) {
            adjustments.add("minimum active roster raised to " + minimumActiveFloor
                    + " (" + MINIMUM_ACTIVE_PERCENT + "% of map allocation)");
            recommendedMinimum = minimumActiveFloor;
        }
        recommendedMaximum = Math.max(recommendedMinimum, recommendedMaximum);

        return new AgentFieldCapacityCatalog.MapCapacity(
                evidence.mapId(), evidence.mapName(), recommendedMinimum,
                recommendedMaximum, estimatedMaximum,
                activeCounts(recommendedMinimum, recommendedMaximum, estimatedMaximum),
                balancedParties(estimatedMaximum), source, confidence,
                totalSpawns, rawCapacity, spawnBudget, accessPenalty,
                List.copyOf(platforms), adjustments.stream()
                        .filter(value -> value != null && !value.isBlank()).toList());
    }

    private static List<Integer> activeCounts(int minimum, int recommended, int maximum) {
        LinkedHashSet<Integer> ascending = new LinkedHashSet<>();
        ascending.add(minimum);
        ascending.add(recommended);
        ascending.add(maximum);
        ArrayList<Integer> result = new ArrayList<>(ascending);
        ArrayList<Integer> descending = new ArrayList<>(result);
        java.util.Collections.reverse(descending);
        for (int count : descending.subList(1, descending.size())) {
            result.add(count);
        }
        return List.copyOf(result);
    }

    private static List<Integer> balancedParties(int maximum) {
        int parties = divideRoundUp(maximum, 6);
        int base = maximum / parties;
        int remainder = maximum % parties;
        ArrayList<Integer> sizes = new ArrayList<>();
        for (int index = 0; index < parties; index++) {
            sizes.add(base + (index < remainder ? 1 : 0));
        }
        return List.copyOf(sizes);
    }

    private static int divideRoundUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    public record MapEvidence(
            int mapId,
            String mapName,
            int totalSpawnEntries,
            int climbableCount,
            String complexity,
            List<SpawnEvidence> spawnPoints) {
        public MapEvidence {
            spawnPoints = List.copyOf(spawnPoints);
        }
    }

    public record SpawnEvidence(int componentId, int x, int y) {
    }

    public record CapacityOverride(
            Integer recommendedMinimum,
            Integer recommendedMaximum,
            Integer maximumAgents,
            String confidence,
            String reason) {
    }
}
