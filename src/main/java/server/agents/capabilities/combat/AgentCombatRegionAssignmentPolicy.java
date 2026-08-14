package server.agents.capabilities.combat;

import server.agents.catalog.AgentMapRegionAssignment;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

/** Converts a coordinator assignment into a soft target-acquisition scope. */
public final class AgentCombatRegionAssignmentPolicy {
    private AgentCombatRegionAssignmentPolicy() {
    }

    public static <T> SearchScope<T> begin(
            AgentRuntimeEntry entry,
            AgentCombatDirective directive,
            int currentMapId,
            List<T> localCandidates,
            ToIntFunction<T> regionResolver,
            long nowMs) {
        if (entry == null || directive == null || localCandidates == null || regionResolver == null) {
            return SearchScope.unrestricted(localCandidates);
        }
        AgentMapRegionAssignment assignment = directive.regionAssignment();
        Set<Integer> assignedRegions = assignedRegions(assignment, currentMapId, nowMs);
        if (assignedRegions.isEmpty()) {
            return SearchScope.unrestricted(localCandidates);
        }
        List<T> assigned = localCandidates.stream()
                .filter(candidate -> assignedRegions.contains(regionResolver.applyAsInt(candidate)))
                .toList();
        boolean borrowing = entry.capabilityStates()
                .require(AgentCombatRegionAssignmentState.STATE_KEY)
                .observe(assignment.assignmentId(), !assigned.isEmpty(),
                        AgentCombatPolicyConfig.regionAssignmentBorrowEmptyScans(), nowMs,
                        AgentCombatPolicyConfig.regionAssignmentBorrowMs());
        if (borrowing) {
            return new SearchScope<>(List.copyOf(localCandidates), Set.of(), true);
        }
        return new SearchScope<>(List.copyOf(assigned), assignedRegions, false);
    }

    static Set<Integer> assignedRegions(
            AgentMapRegionAssignment assignment, int currentMapId, long nowMs) {
        if (assignment == null || assignment.mapId() != currentMapId
                || nowMs >= assignment.expiresAtMs()) {
            return Set.of();
        }
        LinkedHashSet<Integer> regions = new LinkedHashSet<>();
        for (String regionId : assignment.regionIds()) {
            try {
                int parsed = Integer.parseInt(regionId);
                if (parsed >= 0) {
                    regions.add(parsed);
                }
            } catch (NumberFormatException ignored) {
                // Curated future region signatures are ignored until resolved by their catalog adapter.
            }
        }
        return Set.copyOf(regions);
    }

    public record SearchScope<T>(
            List<T> localCandidates,
            Set<Integer> assignedRegions,
            boolean borrowing) {
        public SearchScope {
            localCandidates = localCandidates == null ? List.of() : List.copyOf(localCandidates);
            assignedRegions = assignedRegions == null ? Set.of() : Set.copyOf(assignedRegions);
        }

        static <T> SearchScope<T> unrestricted(List<T> candidates) {
            return new SearchScope<>(candidates == null ? List.of() : candidates, Set.of(), true);
        }

        public List<T> apply(List<T> candidates, ToIntFunction<T> regionResolver) {
            if (candidates == null || candidates.isEmpty() || borrowing || assignedRegions.isEmpty()) {
                return candidates == null ? List.of() : new ArrayList<>(candidates);
            }
            return candidates.stream()
                    .filter(candidate -> assignedRegions.contains(regionResolver.applyAsInt(candidate)))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
    }
}
