package server.agents.progression;

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

        if (policy.preserveCurrentMapWhenEligible()) {
            Optional<Selection> current = candidates.stream()
                    .filter(candidate -> candidate.map().mapId() == currentMapId)
                    .filter(candidate -> candidate.choice().rank() <= policy.currentMapMaximumRank())
                    .filter(candidate -> candidate.occupancy() <= candidate.map().recommendedAgents())
                    .findFirst();
            if (current.isPresent()) {
                Selection selected = current.get();
                return Optional.of(new Selection(selected.choice(), selected.map(), selected.occupancy(),
                        "retain eligible current map to avoid level-by-level churn"));
            }
        }

        Optional<Selection> belowSoftCapacity = candidates.stream()
                .filter(candidate -> candidate.occupancy() < candidate.map().recommendedAgents())
                .findFirst();
        if (belowSoftCapacity.isPresent()) {
            Selection selected = belowSoftCapacity.get();
            return Optional.of(new Selection(selected.choice(), selected.map(), selected.occupancy(),
                    "highest-ranked eligible map below recommended occupancy"));
        }

        return candidates.stream().findFirst().map(selected ->
                new Selection(selected.choice(), selected.map(), selected.occupancy(),
                        "all preferred maps reached soft capacity; using highest-ranked map below hard capacity"));
    }

    private Selection candidate(AgentVictoriaTrainingCatalog.TrainingChoice choice,
                                Map<Integer, Integer> occupancyByMap) {
        AgentVictoriaTrainingCatalog.TrainingMap map = repository.findMap(choice.mapId()).orElseThrow();
        int occupancy = occupancyByMap == null ? 0 : Math.max(0, occupancyByMap.getOrDefault(map.mapId(), 0));
        return new Selection(choice, map, occupancy, "candidate");
    }
}
