package server.agents.population.allocation;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Shared deterministic soft/hard-capacity policy. It selects placement but
 * never warps an Agent or mutates occupancy.
 */
public final class AgentMapCapacityAllocator {
    public Optional<AgentMapCapacityDecision> select(
            List<AgentMapCapacityCandidate> candidates,
            int currentMapId,
            boolean preserveCurrentMap,
            int currentMapMaximumRank) {
        if (candidates == null || currentMapMaximumRank < 0) {
            throw new IllegalArgumentException("Candidates and a non-negative current-map rank are required");
        }

        List<AgentMapCapacityCandidate> eligible = candidates.stream()
                .filter(candidate -> candidate != null && candidate.belowHardCapacity())
                .sorted(Comparator.comparingInt(AgentMapCapacityCandidate::rank)
                        .thenComparingInt(AgentMapCapacityCandidate::mapId))
                .toList();

        if (preserveCurrentMap) {
            Optional<AgentMapCapacityCandidate> current = eligible.stream()
                    .filter(candidate -> candidate.mapId() == currentMapId)
                    .filter(candidate -> candidate.rank() <= currentMapMaximumRank)
                    .filter(candidate -> candidate.occupancy() <= candidate.recommendedCapacity())
                    .findFirst();
            if (current.isPresent()) {
                return Optional.of(new AgentMapCapacityDecision(
                        current.get(),
                        AgentMapCapacityDecision.Reason.RETAIN_ELIGIBLE_CURRENT_MAP));
            }
        }

        Optional<AgentMapCapacityCandidate> soft = eligible.stream()
                .filter(AgentMapCapacityCandidate::belowSoftCapacity)
                .findFirst();
        if (soft.isPresent()) {
            return Optional.of(new AgentMapCapacityDecision(
                    soft.get(),
                    AgentMapCapacityDecision.Reason.HIGHEST_RANKED_BELOW_SOFT_CAPACITY));
        }

        return eligible.stream().findFirst().map(candidate -> new AgentMapCapacityDecision(
                candidate,
                AgentMapCapacityDecision.Reason.HIGHEST_RANKED_BELOW_HARD_CAPACITY));
    }
}
