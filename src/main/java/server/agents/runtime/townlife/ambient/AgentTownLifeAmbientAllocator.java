package server.agents.runtime.townlife.ambient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure bounded weighted allocation shared by initial placement and rebalancing. */
public final class AgentTownLifeAmbientAllocator {
    private AgentTownLifeAmbientAllocator() {
    }

    public static Map<Integer, Integer> allocate(
            int requested, List<AgentTownLifeAmbientManifest.Town> towns) {
        if (requested < 0 || towns == null || towns.isEmpty()) {
            throw new IllegalArgumentException("valid ambient TownLife allocation is required");
        }
        int capacity = towns.stream().mapToInt(AgentTownLifeAmbientManifest.Town::maxActive).sum();
        int target = Math.min(requested, capacity);
        LinkedHashMap<Integer, Integer> result = new LinkedHashMap<>();
        towns.forEach(town -> result.put(town.mapId(), 0));
        int minimumTotal = towns.stream().mapToInt(AgentTownLifeAmbientManifest.Town::minActive).sum();
        if (target >= minimumTotal) {
            for (AgentTownLifeAmbientManifest.Town town : towns) {
                result.put(town.mapId(), town.minActive());
            }
        }
        while (result.values().stream().mapToInt(Integer::intValue).sum() < target) {
            AgentTownLifeAmbientManifest.Town selected = null;
            double best = Double.NEGATIVE_INFINITY;
            for (AgentTownLifeAmbientManifest.Town town : towns) {
                int current = result.get(town.mapId());
                if (current >= town.maxActive()) {
                    continue;
                }
                double score = (double) town.allocationWeight() / (current + 1);
                if (selected == null || score > best) {
                    selected = town;
                    best = score;
                }
            }
            if (selected == null) {
                break;
            }
            result.compute(selected.mapId(), (ignored, count) -> count + 1);
        }
        return Map.copyOf(result);
    }

    /** Assigns the reusable roster; inactive standby is not constrained by active town caps. */
    public static Map<Integer, Integer> allocateRoster(
            int requested, List<AgentTownLifeAmbientManifest.Town> towns) {
        if (requested < 0 || towns == null || towns.isEmpty()) {
            throw new IllegalArgumentException("valid ambient TownLife roster allocation is required");
        }
        LinkedHashMap<Integer, Integer> result = new LinkedHashMap<>();
        towns.forEach(town -> result.put(town.mapId(), 0));
        while (result.values().stream().mapToInt(Integer::intValue).sum() < requested) {
            AgentTownLifeAmbientManifest.Town selected = null;
            double best = Double.NEGATIVE_INFINITY;
            for (AgentTownLifeAmbientManifest.Town town : towns) {
                int current = result.get(town.mapId());
                double score = (double) town.allocationWeight() / (current + 1);
                if (selected == null || score > best) {
                    selected = town;
                    best = score;
                }
            }
            result.compute(selected.mapId(), (ignored, count) -> count + 1);
        }
        return Map.copyOf(result);
    }
}
