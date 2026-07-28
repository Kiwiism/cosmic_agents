package server.agents.progression;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves fixed and generated hunt-map choices behind one rollout policy.
 * Generated facts never mutate a plan; they only supply a map choice when policy permits it.
 */
final class AgentAdaptiveQuestHuntSelector {
    private static final Logger log = LoggerFactory.getLogger(AgentAdaptiveQuestHuntSelector.class);
    private static final AgentAdaptiveQuestHuntSelector DEFAULT = new AgentAdaptiveQuestHuntSelector(
            AgentVictoriaQuestHuntPolicyRepository.defaultRepository(),
            AgentVictoriaQuestHuntIndexRepository.defaultRepository());

    private final AgentVictoriaQuestHuntPolicyRepository policyRepository;
    private final AgentVictoriaQuestHuntIndexRepository indexRepository;
    private final Map<Integer, String> lastShadowSignatureByAgent = new ConcurrentHashMap<>();

    AgentAdaptiveQuestHuntSelector(
            AgentVictoriaQuestHuntPolicyRepository policyRepository,
            AgentVictoriaQuestHuntIndexRepository indexRepository) {
        this.policyRepository = policyRepository;
        this.indexRepository = indexRepository;
    }

    static AgentAdaptiveQuestHuntSelector defaultSelector() {
        return DEFAULT;
    }

    Optional<Selection> select(
            AgentRuntimeEntry entry,
            Character agent,
            int questId,
            String objectiveId,
            List<AgentVictoriaQuestRuntimeCatalog.HuntMap> preferred,
            boolean mvpPlan) {
        AgentVictoriaQuestHuntPolicy policy = policyRepository.policy();
        AgentQuestHuntSelectionMode mode = policy.modeFor(questId, mvpPlan);
        List<AgentVictoriaQuestHuntIndex.Candidate> adaptive = indexRepository
                .findObjective(questId, objectiveId)
                .map(AgentVictoriaQuestHuntIndex.Objective::candidates)
                .orElse(List.of());

        Set<Integer> candidateMapIds = new LinkedHashSet<>();
        preferred.forEach(candidate -> candidateMapIds.add(candidate.mapId()));
        adaptive.forEach(candidate -> candidateMapIds.add(candidate.mapId()));
        Set<Integer> routeEligibleMapIds = new LinkedHashSet<>();
        for (int mapId : candidateMapIds) {
            if (AgentVictoriaTrainingRouteCatalog.canRoute(agent.getMapId(), mapId)) {
                routeEligibleMapIds.add(mapId);
            }
        }
        Map<Integer, Integer> occupancy =
                AgentVictoriaTrainingPopulation.snapshot(agent, routeEligibleMapIds);
        AgentProgressionProfile profile = AgentProgressionProfileRuntime.profile(entry);

        AgentVictoriaQuestRuntimeCatalog.HuntMap preferredChoice = choosePreferred(
                preferred, routeEligibleMapIds, occupancy, profile, agent, mvpPlan);
        AdaptiveChoice adaptiveChoice = chooseAdaptive(
                adaptive, routeEligibleMapIds, occupancy, profile, agent);
        if (policy.shadowModeEnabled()) {
            recordShadow(agent, questId, objectiveId, mode, preferredChoice, adaptiveChoice);
        }

        if (mode == AgentQuestHuntSelectionMode.FIXED) {
            return Optional.ofNullable(preferredChoice)
                    .map(map -> new Selection(map, mode, Source.FIXED_PREFERRED, adaptiveChoice));
        }
        if (mode == AgentQuestHuntSelectionMode.PREFERRED_ADAPTIVE) {
            if (preferredChoice != null) {
                return Optional.of(new Selection(
                        preferredChoice, mode, Source.FIXED_PREFERRED, adaptiveChoice));
            }
            if (policy.adaptiveFallbackEnabled() && adaptiveChoice != null) {
                return Optional.of(new Selection(
                        adaptiveChoice.map(), mode, Source.ADAPTIVE_FALLBACK, adaptiveChoice));
            }
            return Optional.empty();
        }
        if (adaptiveChoice != null) {
            return Optional.of(new Selection(
                    adaptiveChoice.map(), mode, Source.ADAPTIVE_PRIMARY, adaptiveChoice));
        }
        return Optional.ofNullable(preferredChoice)
                .map(map -> new Selection(map, mode, Source.FIXED_SAFETY_FALLBACK, null));
    }

    private static AgentVictoriaQuestRuntimeCatalog.HuntMap choosePreferred(
            List<AgentVictoriaQuestRuntimeCatalog.HuntMap> preferred,
            Set<Integer> eligibleMapIds,
            Map<Integer, Integer> occupancy,
            AgentProgressionProfile profile,
            Character agent,
            boolean mvpPlan) {
        if (mvpPlan) {
            return preferred.stream()
                    .filter(map -> eligibleMapIds.contains(map.mapId()))
                    .filter(map -> occupancy.getOrDefault(map.mapId(), 0) < map.recommendedAgents())
                    .findFirst()
                    .or(() -> preferred.stream()
                            .filter(map -> eligibleMapIds.contains(map.mapId()))
                            .filter(map -> occupancy.getOrDefault(map.mapId(), 0) < map.maximumAgents())
                            .findFirst())
                    .orElse(null);
        }
        return preferred.stream()
                .filter(map -> eligibleMapIds.contains(map.mapId()))
                .filter(map -> occupancy.getOrDefault(map.mapId(), 0) < map.maximumAgents())
                .max(Comparator.comparingLong(map -> AgentProgressionDecisionPolicy.huntMapScore(
                        profile, agent.getId(), agent.getLevel(), agent.getMapId(), map,
                        occupancy.getOrDefault(map.mapId(), 0))))
                .orElse(null);
    }

    private static AdaptiveChoice chooseAdaptive(
            List<AgentVictoriaQuestHuntIndex.Candidate> candidates,
            Set<Integer> eligibleMapIds,
            Map<Integer, Integer> occupancy,
            AgentProgressionProfile profile,
            Character agent) {
        List<AdaptiveChoice> choices = new ArrayList<>();
        for (AgentVictoriaQuestHuntIndex.Candidate candidate : candidates) {
            AgentVictoriaQuestRuntimeCatalog.HuntMap map = candidate.asHuntMap();
            int population = occupancy.getOrDefault(map.mapId(), 0);
            if (!eligibleMapIds.contains(map.mapId()) || population >= map.maximumAgents()) {
                continue;
            }
            long runtimeScore = AgentProgressionDecisionPolicy.huntMapScore(
                    profile, agent.getId(), agent.getLevel(), agent.getMapId(), map, population);
            long totalScore = candidate.score() * 1_000L + runtimeScore;
            choices.add(new AdaptiveChoice(map, candidate, totalScore));
        }
        return choices.stream()
                .max(Comparator.comparingLong(AdaptiveChoice::runtimeAdjustedScore)
                        .thenComparingInt(choice -> -choice.candidate().rank()))
                .orElse(null);
    }

    private void recordShadow(
            Character agent,
            int questId,
            String objectiveId,
            AgentQuestHuntSelectionMode mode,
            AgentVictoriaQuestRuntimeCatalog.HuntMap preferred,
            AdaptiveChoice adaptive) {
        int preferredMapId = preferred == null ? 0 : preferred.mapId();
        int adaptiveMapId = adaptive == null ? 0 : adaptive.map().mapId();
        String signature = questId + "|" + objectiveId + "|" + mode
                + "|" + preferredMapId + "|" + adaptiveMapId;
        if (signature.equals(lastShadowSignatureByAgent.put(agent.getId(), signature))) {
            return;
        }
        String evidence = adaptive == null || adaptive.candidate().scoreEvidence() == null
                ? "none" : adaptive.candidate().scoreEvidence().summary();
        log.info("Agent hunt shadow agent={} quest={} objective={} mode={} fixedMap={} "
                        + "adaptiveMap={} adaptiveCatalogScore={} adaptiveRuntimeScore={} evidence={}",
                agent.getName(), questId, objectiveId, mode, preferredMapId, adaptiveMapId,
                adaptive == null ? 0L : adaptive.candidate().score(),
                adaptive == null ? 0L : adaptive.runtimeAdjustedScore(), evidence);
    }

    record Selection(
            AgentVictoriaQuestRuntimeCatalog.HuntMap map,
            AgentQuestHuntSelectionMode mode,
            Source source,
            AdaptiveChoice adaptiveEvidence) {
    }

    record AdaptiveChoice(
            AgentVictoriaQuestRuntimeCatalog.HuntMap map,
            AgentVictoriaQuestHuntIndex.Candidate candidate,
            long runtimeAdjustedScore) {
    }

    enum Source {
        FIXED_PREFERRED,
        ADAPTIVE_FALLBACK,
        ADAPTIVE_PRIMARY,
        FIXED_SAFETY_FALLBACK
    }
}
