package server.agents.progression;

import server.agents.population.allocation.AgentMapCapacityAllocator;
import server.agents.population.allocation.AgentMapCapacityCandidate;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Deterministic ranked/capacity selection; live policy supplies occupancy and route eligibility. */
public final class AgentVictoriaTrainingMapSelector {
    public record Selection(
            AgentVictoriaTrainingCatalog.TrainingChoice choice,
            AgentVictoriaTrainingCatalog.TrainingMap map,
            int occupancy,
            String reason) {
    }

    private final AgentVictoriaTrainingCatalogRepository repository;
    private final AgentMapCapacityAllocator capacityAllocator = new AgentMapCapacityAllocator();

    public AgentVictoriaTrainingMapSelector(AgentVictoriaTrainingCatalogRepository repository) {
        this.repository = repository;
    }

    public Optional<Selection> select(int level,
                                      int currentMapId,
                                      Map<Integer, Integer> occupancyByMap,
                                      Set<Integer> eligibleMapIds) {
        return select(level, currentMapId, occupancyByMap, eligibleMapIds, null, 0);
    }

    public Optional<Selection> select(int level,
                                      int currentMapId,
                                      Map<Integer, Integer> occupancyByMap,
                                      Set<Integer> eligibleMapIds,
                                      AgentProgressionProfile profile,
                                      int characterId) {
        AgentVictoriaTrainingCatalog.SelectionPolicy policy = repository.catalog().selectionPolicy();
        List<Selection> candidates = repository.choicesForLevel(level).stream()
                .sorted(Comparator.comparingInt(AgentVictoriaTrainingCatalog.TrainingChoice::rank))
                .filter(choice -> eligibleMapIds == null || eligibleMapIds.contains(choice.mapId()))
                .map(choice -> candidate(choice, occupancyByMap))
                .filter(candidate -> candidate.occupancy() < candidate.map().maximumAgents())
                .toList();

        if (profile != null) {
            return candidates.stream()
                    .max(Comparator.comparingLong(candidate -> AgentProgressionDecisionPolicy.trainingMapScore(
                            profile, characterId, level, currentMapId, candidate.choice(),
                            candidate.map(), candidate.occupancy())))
                    .map(selected -> new Selection(selected.choice(), selected.map(), selected.occupancy(),
                            "personality=" + profile.profileId() + "; weighted quest/grind map score"));
        }

        return capacityAllocator.select(
                        candidates.stream().map(candidate -> new AgentMapCapacityCandidate(
                                candidate.map().mapId(),
                                candidate.choice().rank(),
                                candidate.occupancy(),
                                candidate.map().recommendedAgents(),
                                candidate.map().maximumAgents())).toList(),
                        currentMapId,
                        policy.preserveCurrentMapWhenEligible(),
                        policy.currentMapMaximumRank())
                .map(decision -> candidates.stream()
                        .filter(candidate -> candidate.map().mapId() == decision.candidate().mapId())
                        .findFirst()
                        .map(selected -> new Selection(
                                selected.choice(),
                                selected.map(),
                                selected.occupancy(),
                                switch (decision.reason()) {
                                    case RETAIN_ELIGIBLE_CURRENT_MAP ->
                                            "retain eligible current map to avoid level-by-level churn";
                                    case HIGHEST_RANKED_BELOW_SOFT_CAPACITY ->
                                            "highest-ranked eligible map below recommended occupancy";
                                    case HIGHEST_RANKED_BELOW_HARD_CAPACITY ->
                                            "all preferred maps reached soft capacity; using highest-ranked map below hard capacity";
                                }))
                        .orElseThrow());
    }

    private Selection candidate(AgentVictoriaTrainingCatalog.TrainingChoice choice,
                                Map<Integer, Integer> occupancyByMap) {
        AgentVictoriaTrainingCatalog.TrainingMap map = repository.findMap(choice.mapId()).orElseThrow();
        int occupancy = occupancyByMap == null ? 0 : Math.max(0, occupancyByMap.getOrDefault(map.mapId(), 0));
        return new Selection(choice, map, occupancy, "candidate");
    }
}
