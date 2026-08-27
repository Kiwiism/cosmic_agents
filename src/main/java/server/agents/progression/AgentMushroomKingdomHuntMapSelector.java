package server.agents.progression;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Mushroom Kingdom-only soft-cap placement with proportional overflow balancing. */
final class AgentMushroomKingdomHuntMapSelector {
    record Selection(AgentMushroomKingdomCatalog.HuntMap map, int occupancy, boolean overflow) {
    }

    private AgentMushroomKingdomHuntMapSelector() {
    }

    static Optional<Selection> select(List<AgentMushroomKingdomCatalog.HuntMap> rankedMaps,
                                      Map<Integer, Integer> occupancyByMap) {
        if (rankedMaps == null || rankedMaps.isEmpty()) return Optional.empty();
        List<Selection> candidates = rankedMaps.stream()
                .map(map -> new Selection(map, occupancy(map, occupancyByMap), false))
                .toList();

        Optional<Selection> available = candidates.stream()
                .filter(candidate -> candidate.occupancy() < candidate.map().recommendedMaximum())
                .findFirst();
        if (available.isPresent()) return available;

        Comparator<Selection> normalizedCrowd = (left, right) -> {
            long leftPressure = (long) left.occupancy() * right.map().recommendedMaximum();
            long rightPressure = (long) right.occupancy() * left.map().recommendedMaximum();
            return Long.compare(leftPressure, rightPressure);
        };
        return candidates.stream()
                .min(normalizedCrowd)
                .map(selected -> new Selection(selected.map(), selected.occupancy(), true));
    }

    private static int occupancy(AgentMushroomKingdomCatalog.HuntMap map,
                                 Map<Integer, Integer> occupancyByMap) {
        return occupancyByMap == null ? 0
                : Math.max(0, occupancyByMap.getOrDefault(map.mapId(), 0));
    }
}
