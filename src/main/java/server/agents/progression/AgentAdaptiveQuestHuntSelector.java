package server.agents.progression;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
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
    private static final long SHARED_SELECTION_LEASE_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentAdaptiveQuestHuntSelector.SHARED_SELECTION_LEASE_MS");
    private static final int DEFAULT_RECOMMENDED_AGENTS = config.AgentTuning.intValue(
            "server.agents.progression.AgentAdaptiveQuestHuntSelector.DEFAULT_RECOMMENDED_AGENTS");
    private static final int DEFAULT_MAXIMUM_AGENTS = config.AgentTuning.intValue(
            "server.agents.progression.AgentAdaptiveQuestHuntSelector.DEFAULT_MAXIMUM_AGENTS");
    private static final int ROUTE_HOP_COST = config.AgentTuning.intValue(
            "server.agents.progression.AgentAdaptiveQuestHuntSelector.ROUTE_HOP_COST");
    private static final int SWEEP_BASE_COST = config.AgentTuning.intValue(
            "server.agents.progression.AgentAdaptiveQuestHuntSelector.SWEEP_BASE_COST");
    private static final int TOPOLOGY_COST_PERCENT = config.AgentTuning.intValue(
            "server.agents.progression.AgentAdaptiveQuestHuntSelector.TOPOLOGY_COST_PERCENT");
    private static final int OCCUPIED_AGENT_COST = config.AgentTuning.intValue(
            "server.agents.progression.AgentAdaptiveQuestHuntSelector.OCCUPIED_AGENT_COST");
    private static final int LEVEL_GAP_COST = config.AgentTuning.intValue(
            "server.agents.progression.AgentAdaptiveQuestHuntSelector.LEVEL_GAP_COST");
    private static final int PREFERRED_MAP_CREDIT = config.AgentTuning.intValue(
            "server.agents.progression.AgentAdaptiveQuestHuntSelector.PREFERRED_MAP_CREDIT");
    private static final int MISSING_OBJECTIVE_COST = config.AgentTuning.intValue(
            "server.agents.progression.AgentAdaptiveQuestHuntSelector.MISSING_OBJECTIVE_COST");
    private static final int REPLAN_SWITCH_MARGIN_COST = config.AgentTuning.intValue(
            "server.agents.progression.AgentAdaptiveQuestHuntSelector.REPLAN_SWITCH_MARGIN_COST");

    private final AgentVictoriaQuestHuntPolicyRepository policyRepository;
    private final AgentVictoriaQuestHuntIndexRepository indexRepository;
    private final Map<Integer, String> lastShadowSignatureByAgent = new ConcurrentHashMap<>();
    private final Map<SharedSelectionKey, SharedSelectionLease> sharedSelectionLeases =
            new ConcurrentHashMap<>();
    private final Map<SharedSelectionKey, String> lastDecisionSignatures =
            new ConcurrentHashMap<>();

    AgentAdaptiveQuestHuntSelector(
            AgentVictoriaQuestHuntPolicyRepository policyRepository,
            AgentVictoriaQuestHuntIndexRepository indexRepository) {
        this.policyRepository = policyRepository;
        this.indexRepository = indexRepository;
    }

    static AgentAdaptiveQuestHuntSelector defaultSelector() {
        return DEFAULT;
    }

    Optional<Selection> select(AgentHuntSelectionRequest request) {
        AgentVictoriaQuestHuntPolicy policy = policyRepository.policy();
        AgentHuntSelectionRequest.ObjectiveDemand primary = request.objectives().getFirst();
        AgentQuestHuntSelectionMode mode = policy.modeFor(primary.questId(), request.mvpPlan());
        Map<Integer, DeficitCandidateBuilder> builders = new HashMap<>();
        for (AgentHuntSelectionRequest.ObjectiveDemand demand : request.objectives()) {
            List<AgentVictoriaQuestHuntIndex.Candidate> candidates = indexRepository
                    .findObjective(demand.questId(), demand.objectiveId())
                    .map(AgentVictoriaQuestHuntIndex.Objective::candidates)
                    .orElseGet(() -> indexRepository.findCandidatesForMobs(demand.sourceMobIds()));
            for (AgentVictoriaQuestHuntIndex.Candidate candidate : candidates) {
                builders.computeIfAbsent(candidate.mapId(),
                                ignored -> new DeficitCandidateBuilder(candidate))
                        .add(demand, candidate);
            }
        }
        for (AgentVictoriaQuestRuntimeCatalog.HuntMap preferred : request.preferredMaps()) {
            builders.computeIfAbsent(preferred.mapId(),
                    ignored -> new DeficitCandidateBuilder(preferred));
        }

        Set<Integer> routeEligibleMapIds = builders.keySet().stream()
                .filter(mapId -> !request.excludedMapIds().contains(mapId))
                .filter(mapId -> AgentVictoriaTrainingRouteCatalog.canRoute(
                        request.agent().getMapId(), mapId))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<Integer, Integer> occupancy = AgentVictoriaTrainingPopulation.snapshot(
                request.agent(), routeEligibleMapIds);
        AgentProgressionProfile profile = AgentProgressionProfileRuntime.profile(request.entry());
        List<DeficitChoice> choices = builders.values().stream()
                .map(DeficitCandidateBuilder::build)
                .filter(choice -> routeEligibleMapIds.contains(choice.map().mapId()))
                .filter(choice -> occupancy.getOrDefault(choice.map().mapId(), 0)
                        < choice.map().maximumAgents())
                .map(choice -> evaluate(request, choice, occupancy, profile))
                .sorted(Comparator.comparingLong(DeficitChoice::completionCost)
                        .thenComparingInt(choice -> choice.map().rank())
                        .thenComparingInt(choice -> choice.map().mapId()))
                .toList();
        if (choices.isEmpty()) {
            return Optional.empty();
        }

        DeficitChoice preferred = request.preferredMaps().stream()
                .map(map -> choices.stream()
                        .filter(choice -> choice.map().mapId() == map.mapId())
                        .findFirst().orElse(null))
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        boolean deficitHasAuthority = request.reason() != AgentHuntSelectionRequest.Reason.NORMAL
                || !request.mvpPlan() || mode == AgentQuestHuntSelectionMode.ADAPTIVE;
        String objectiveSignature = request.objectives().stream()
                .map(demand -> demand.questId() + ":" + demand.objectiveId())
                .sorted().reduce((left, right) -> left + "|" + right).orElse("");
        SharedSelectionKey leaseKey = new SharedSelectionKey(
                request.agent().getId(), request.selectionId());
        SharedSelectionLease lease = sharedSelectionLeases.get(leaseKey);
        DeficitChoice leasedChoice = lease == null ? null : choices.stream()
                .filter(choice -> choice.map().mapId() == lease.mapId())
                .findFirst().orElse(null);
        boolean leaseValid = deficitHasAuthority && lease != null
                && lease.objectiveSignature().equals(objectiveSignature)
                && lease.expiresAtMs() > request.nowMs() && leasedChoice != null;
        if (!leaseValid) {
            sharedSelectionLeases.remove(leaseKey);
        }
        DeficitChoice best = choices.getFirst();
        boolean materiallyBetter = leaseValid
                && best.map().mapId() != leasedChoice.map().mapId()
                && best.completionCost() + REPLAN_SWITCH_MARGIN_COST
                < leasedChoice.completionCost();
        DeficitChoice selected = !deficitHasAuthority && preferred != null
                ? preferred : leaseValid && !materiallyBetter ? leasedChoice : best;
        if (materiallyBetter) {
            leaseValid = false;
        }
        remember(leaseKey, selected.map().mapId(), objectiveSignature, request.nowMs());
        Source source = leaseValid
                ? Source.STICKY_SELECTION : selected == preferred
                ? Source.FIXED_PREFERRED
                : request.reason() == AgentHuntSelectionRequest.Reason.NORMAL
                ? Source.DEFICIT_AWARE
                : Source.RECOVERY_FALLBACK;
        recordDeficitChoice(leaseKey, request, selected, choices, mode, source);
        return Optional.of(new Selection(selected.map(), mode, source,
                selected.evidence() == null ? null
                        : new AdaptiveChoice(selected.map(), selected.evidence(),
                        -selected.completionCost())));
    }

    private static DeficitChoice evaluate(
            AgentHuntSelectionRequest request,
            DeficitChoice choice,
            Map<Integer, Integer> occupancy,
            AgentProgressionProfile profile) {
        int routeDistance = AgentVictoriaTrainingRouteCatalog.distance(
                request.agent().getMapId(), choice.map().mapId());
        long travelCost = Math.max(0, routeDistance) * (long) ROUTE_HOP_COST;
        long huntCost = 0L;
        int coveredObjectives = 0;
        for (AgentHuntSelectionRequest.ObjectiveDemand demand : request.objectives()) {
            if (demand.remainingCount() <= 0) {
                continue;
            }
            AgentVictoriaQuestHuntIndex.Candidate evidence =
                    choice.evidenceByObjective().get(objectiveKey(demand));
            if (evidence == null) {
                huntCost += MISSING_OBJECTIVE_COST
                        + demand.remainingCount() * (long) SWEEP_BASE_COST;
                continue;
            }
            coveredObjectives++;
            long yieldBasisPoints = Math.max(1L,
                    evidence.expectedUnitsPerSweepBasisPoints());
            long sweeps = Math.max(1L, divideRoundingUp(
                    demand.remainingCount() * 10_000L, yieldBasisPoints));
            long topologyCost = evidence.targetHorizontalSpan() / 4L
                    + evidence.targetVerticalSpan() / 2L
                    + Math.max(0, evidence.targetComponentCount() - 1) * 1_200L
                    + evidence.climbableCount() * 150L
                    + evidence.scoreEvidence().irrelevantSpawnPenalty() / 10L;
            long sweepCost = SWEEP_BASE_COST
                    + topologyCost * TOPOLOGY_COST_PERCENT / 100L;
            huntCost += sweeps * sweepCost;
        }
        int population = occupancy.getOrDefault(choice.map().mapId(), 0);
        long occupancyCost = population * (long) OCCUPIED_AGENT_COST;
        int levelGap = choice.evidence() == null ? 0
                : Math.max(0, choice.evidence().maxMobLevel() - request.agent().getLevel() - 2);
        long riskCost = levelGap * (long) LEVEL_GAP_COST;
        boolean preferred = request.preferredMaps().stream()
                .anyMatch(map -> map.mapId() == choice.map().mapId());
        long preferredCredit = preferred ? PREFERRED_MAP_CREDIT : 0L;
        long currentMapCredit = choice.map().mapId() == request.agent().getMapId()
                ? profile.routinePreference() * 20L : 0L;
        long completionCost = Math.max(0L, travelCost + huntCost + occupancyCost + riskCost
                - preferredCredit - currentMapCredit);
        return choice.withCosts(completionCost, travelCost, huntCost, coveredObjectives);
    }

    private void recordDeficitChoice(
            SharedSelectionKey key,
            AgentHuntSelectionRequest request,
            DeficitChoice selected,
            List<DeficitChoice> choices,
            AgentQuestHuntSelectionMode mode,
            Source source) {
        String alternatives = choices.stream().limit(3)
                .map(choice -> choice.map().mapId() + ":" + choice.completionCost()
                        + "(travel=" + choice.travelCost() + ",hunt=" + choice.huntCost()
                        + ",coverage=" + choice.coveredObjectives() + ")")
                .reduce((left, right) -> left + "," + right).orElse("none");
        String remaining = request.objectives().stream()
                .map(demand -> demand.objectiveId() + "=" + demand.remainingCount())
                .reduce((left, right) -> left + "," + right).orElse("none");
        String signature = source + "|" + remaining + "|" + selected.map().mapId()
                + "|" + selected.completionCost() + "|" + alternatives;
        if (signature.equals(lastDecisionSignatures.put(key, signature))) {
            return;
        }
        log.info("Agent hunt choice agent={} selection={} reason={} mode={} source={} "
                        + "remaining={} selectedMap={} completionCost={} alternatives={}",
                request.agent().getName(), request.selectionId(), request.reason(), mode, source,
                remaining, selected.map().mapId(), selected.completionCost(), alternatives);
    }

    private static String objectiveKey(AgentHuntSelectionRequest.ObjectiveDemand demand) {
        return demand.questId() + ":" + demand.objectiveId();
    }

    private static long divideRoundingUp(long value, long divisor) {
        return (value + divisor - 1L) / divisor;
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

    Optional<Selection> selectCombined(
            AgentRuntimeEntry entry,
            Character agent,
            String selectionId,
            int preferredMapId,
            Collection<Integer> preferredMobIds,
            List<AgentVictoriaQuestHuntIndexRepository.ObjectiveReference> objectives,
            long nowMs) {
        if (selectionId == null || selectionId.isBlank()
                || preferredMapId <= 0 || objectives == null || objectives.isEmpty()) {
            return Optional.empty();
        }
        AgentVictoriaQuestHuntPolicy policy = policyRepository.policy();
        boolean adaptiveAllowed = policy.adaptiveFallbackEnabled()
                && objectives.stream().anyMatch(reference ->
                policy.modeFor(reference.questId(), true)
                        != AgentQuestHuntSelectionMode.FIXED);
        String objectiveSignature = objectives.stream()
                .map(reference -> reference.questId() + ":"
                        + reference.objective().objectiveId())
                .sorted()
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
        Map<Integer, CombinedAdaptiveChoice> choices =
                combineAdaptiveChoices(objectives);
        Set<Integer> candidateMapIds = new LinkedHashSet<>(choices.keySet());
        candidateMapIds.add(preferredMapId);
        Set<Integer> routeEligibleMapIds = new LinkedHashSet<>();
        for (int mapId : candidateMapIds) {
            if (AgentVictoriaTrainingRouteCatalog.canRoute(agent.getMapId(), mapId)) {
                routeEligibleMapIds.add(mapId);
            }
        }
        Map<Integer, Integer> occupancy =
                AgentVictoriaTrainingPopulation.snapshot(agent, routeEligibleMapIds);
        AgentProgressionProfile profile = AgentProgressionProfileRuntime.profile(entry);
        SharedSelectionKey leaseKey = new SharedSelectionKey(agent.getId(), selectionId);

        SharedSelectionLease lease = sharedSelectionLeases.get(leaseKey);
        CombinedAdaptiveChoice leasedChoice = lease == null
                ? null : choices.get(lease.mapId());
        if (lease != null && lease.objectiveSignature().equals(objectiveSignature)
                && lease.expiresAtMs() > nowMs
                && routeEligibleMapIds.contains(lease.mapId())
                && occupancy.getOrDefault(lease.mapId(), 0)
                < maximumAgents(lease.mapId(), choices)) {
            AgentVictoriaQuestRuntimeCatalog.HuntMap map = lease.mapId() == preferredMapId
                    ? preferredMap(preferredMapId, preferredMobIds, choices)
                    : leasedChoice.map();
            return Optional.of(new Selection(map, AgentQuestHuntSelectionMode.PREFERRED_ADAPTIVE,
                    Source.STICKY_SELECTION, leasedChoice == null
                    ? null : leasedChoice.asAdaptiveEvidence()));
        }
        sharedSelectionLeases.remove(leaseKey);

        AgentVictoriaQuestRuntimeCatalog.HuntMap preferred =
                preferredMap(preferredMapId, preferredMobIds, choices);
        if (routeEligibleMapIds.contains(preferredMapId)
                && occupancy.getOrDefault(preferredMapId, 0) < preferred.maximumAgents()) {
            remember(leaseKey, preferredMapId, objectiveSignature, nowMs);
            return Optional.of(new Selection(preferred,
                    AgentQuestHuntSelectionMode.PREFERRED_ADAPTIVE,
                    Source.FIXED_PREFERRED, choices.containsKey(preferredMapId)
                    ? choices.get(preferredMapId).asAdaptiveEvidence() : null));
        }
        if (!adaptiveAllowed) {
            return Optional.empty();
        }

        CombinedAdaptiveChoice adaptive = choices.values().stream()
                .filter(choice -> routeEligibleMapIds.contains(choice.map().mapId()))
                .filter(choice -> occupancy.getOrDefault(choice.map().mapId(), 0)
                        < choice.map().maximumAgents())
                .max(Comparator
                        .comparingInt(CombinedAdaptiveChoice::coverageCount)
                        .thenComparingLong(choice -> choice.catalogScore() * 1_000L
                                + AgentProgressionDecisionPolicy.huntMapScore(
                                profile, agent.getId(), agent.getLevel(), agent.getMapId(),
                                choice.map(), occupancy.getOrDefault(choice.map().mapId(), 0)))
                        .thenComparingInt(choice -> -choice.bestRank()))
                .orElse(null);
        if (adaptive == null) {
            return Optional.empty();
        }
        remember(leaseKey, adaptive.map().mapId(), objectiveSignature, nowMs);
        return Optional.of(new Selection(adaptive.map(),
                AgentQuestHuntSelectionMode.PREFERRED_ADAPTIVE,
                adaptive.coverageCount() == objectives.size()
                        ? Source.ADAPTIVE_FALLBACK : Source.ADAPTIVE_DECOMPOSED_FALLBACK,
                adaptive.asAdaptiveEvidence()));
    }

    void clearCombinedSelection(int agentId, String selectionId) {
        if (agentId > 0 && selectionId != null && !selectionId.isBlank()) {
            SharedSelectionKey key = new SharedSelectionKey(agentId, selectionId);
            sharedSelectionLeases.remove(key);
            lastDecisionSignatures.remove(key);
        }
    }

    private void remember(
            SharedSelectionKey key,
            int mapId,
            String objectiveSignature,
            long nowMs) {
        sharedSelectionLeases.put(key,
                new SharedSelectionLease(
                        mapId, objectiveSignature, nowMs + SHARED_SELECTION_LEASE_MS));
    }

    private static Map<Integer, CombinedAdaptiveChoice> combineAdaptiveChoices(
            List<AgentVictoriaQuestHuntIndexRepository.ObjectiveReference> objectives) {
        Map<Integer, CombinedAdaptiveChoiceBuilder> builders = new HashMap<>();
        for (AgentVictoriaQuestHuntIndexRepository.ObjectiveReference reference : objectives) {
            for (AgentVictoriaQuestHuntIndex.Candidate candidate
                    : reference.objective().candidates()) {
                builders.computeIfAbsent(candidate.mapId(),
                                ignored -> new CombinedAdaptiveChoiceBuilder(candidate))
                        .add(reference, candidate);
            }
        }
        Map<Integer, CombinedAdaptiveChoice> choices = new HashMap<>();
        builders.forEach((mapId, builder) -> choices.put(mapId, builder.build()));
        return Map.copyOf(choices);
    }

    private static AgentVictoriaQuestRuntimeCatalog.HuntMap preferredMap(
            int mapId,
            Collection<Integer> preferredMobIds,
            Map<Integer, CombinedAdaptiveChoice> choices) {
        CombinedAdaptiveChoice generated = choices.get(mapId);
        int recommended = generated == null
                ? DEFAULT_RECOMMENDED_AGENTS : generated.map().recommendedAgents();
        int maximum = generated == null
                ? DEFAULT_MAXIMUM_AGENTS : generated.map().maximumAgents();
        List<Integer> mobs = preferredMobIds == null
                ? List.of() : preferredMobIds.stream().distinct().toList();
        if (mobs.isEmpty() && generated != null) {
            mobs = generated.map().targetMobIds();
        }
        return new AgentVictoriaQuestRuntimeCatalog.HuntMap(
                1, mapId, recommended, Math.max(recommended, maximum), mobs);
    }

    private static int maximumAgents(
            int mapId,
            Map<Integer, CombinedAdaptiveChoice> choices) {
        CombinedAdaptiveChoice choice = choices.get(mapId);
        return choice == null ? DEFAULT_MAXIMUM_AGENTS : choice.map().maximumAgents();
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
        ADAPTIVE_DECOMPOSED_FALLBACK,
        ADAPTIVE_PRIMARY,
        FIXED_SAFETY_FALLBACK,
        STICKY_SELECTION,
        DEFICIT_AWARE,
        RECOVERY_FALLBACK
    }

    private record SharedSelectionKey(int agentId, String selectionId) {
    }

    private record SharedSelectionLease(
            int mapId,
            String objectiveSignature,
            long expiresAtMs) {
    }

    private record CombinedAdaptiveChoice(
            AgentVictoriaQuestRuntimeCatalog.HuntMap map,
            AgentVictoriaQuestHuntIndex.Candidate evidence,
            int coverageCount,
            long catalogScore,
            int bestRank) {

        AdaptiveChoice asAdaptiveEvidence() {
            return new AdaptiveChoice(map, evidence, catalogScore);
        }
    }

    private static final class CombinedAdaptiveChoiceBuilder {
        private final AgentVictoriaQuestHuntIndex.Candidate evidence;
        private final Set<String> coveredObjectives = new LinkedHashSet<>();
        private final Set<Integer> targetMobIds = new LinkedHashSet<>();
        private long catalogScore;
        private int recommendedAgents = Integer.MAX_VALUE;
        private int maximumAgents = Integer.MAX_VALUE;
        private int bestRank = Integer.MAX_VALUE;

        private CombinedAdaptiveChoiceBuilder(
                AgentVictoriaQuestHuntIndex.Candidate evidence) {
            this.evidence = evidence;
        }

        private void add(
                AgentVictoriaQuestHuntIndexRepository.ObjectiveReference reference,
                AgentVictoriaQuestHuntIndex.Candidate candidate) {
            String objectiveKey = reference.questId() + ":" + reference.objective().objectiveId();
            if (coveredObjectives.add(objectiveKey)) {
                catalogScore += candidate.score();
            }
            targetMobIds.addAll(candidate.targetMobIds());
            recommendedAgents = Math.min(recommendedAgents, candidate.recommendedAgents());
            maximumAgents = Math.min(maximumAgents, candidate.maximumAgents());
            bestRank = Math.min(bestRank, candidate.rank());
        }

        private CombinedAdaptiveChoice build() {
            AgentVictoriaQuestRuntimeCatalog.HuntMap map =
                    new AgentVictoriaQuestRuntimeCatalog.HuntMap(
                            Math.max(1, bestRank), evidence.mapId(),
                            Math.max(1, recommendedAgents),
                            Math.max(Math.max(1, recommendedAgents), maximumAgents),
                            List.copyOf(targetMobIds));
            return new CombinedAdaptiveChoice(
                    map, evidence, coveredObjectives.size(), catalogScore, bestRank);
        }
    }

    private record DeficitChoice(
            AgentVictoriaQuestRuntimeCatalog.HuntMap map,
            AgentVictoriaQuestHuntIndex.Candidate evidence,
            Map<String, AgentVictoriaQuestHuntIndex.Candidate> evidenceByObjective,
            long completionCost,
            long travelCost,
            long huntCost,
            int coveredObjectives) {

        DeficitChoice withCosts(long completionCost, long travelCost, long huntCost,
                                int coveredObjectives) {
            return new DeficitChoice(map, evidence, evidenceByObjective, completionCost,
                    travelCost, huntCost, coveredObjectives);
        }
    }

    private static final class DeficitCandidateBuilder {
        private final int mapId;
        private final Map<String, AgentVictoriaQuestHuntIndex.Candidate> evidenceByObjective =
                new HashMap<>();
        private final Set<Integer> targetMobIds = new LinkedHashSet<>();
        private AgentVictoriaQuestHuntIndex.Candidate evidence;
        private int rank = Integer.MAX_VALUE;
        private int recommendedAgents = Integer.MAX_VALUE;
        private int maximumAgents = Integer.MAX_VALUE;

        private DeficitCandidateBuilder(AgentVictoriaQuestHuntIndex.Candidate evidence) {
            this.mapId = evidence.mapId();
            include(evidence);
        }

        private DeficitCandidateBuilder(AgentVictoriaQuestRuntimeCatalog.HuntMap map) {
            this.mapId = map.mapId();
            rank = map.rank();
            recommendedAgents = map.recommendedAgents();
            maximumAgents = map.maximumAgents();
            targetMobIds.addAll(map.targetMobIds());
        }

        private void add(AgentHuntSelectionRequest.ObjectiveDemand demand,
                         AgentVictoriaQuestHuntIndex.Candidate candidate) {
            include(candidate);
            evidenceByObjective.put(objectiveKey(demand), candidate);
        }

        private void include(AgentVictoriaQuestHuntIndex.Candidate candidate) {
            if (evidence == null || candidate.score() > evidence.score()) {
                evidence = candidate;
            }
            rank = Math.min(rank, candidate.rank());
            recommendedAgents = Math.min(recommendedAgents, candidate.recommendedAgents());
            maximumAgents = Math.min(maximumAgents, candidate.maximumAgents());
            targetMobIds.addAll(candidate.targetMobIds());
        }

        private DeficitChoice build() {
            int recommended = recommendedAgents == Integer.MAX_VALUE
                    ? DEFAULT_RECOMMENDED_AGENTS : recommendedAgents;
            int maximum = maximumAgents == Integer.MAX_VALUE
                    ? DEFAULT_MAXIMUM_AGENTS : maximumAgents;
            AgentVictoriaQuestRuntimeCatalog.HuntMap map =
                    new AgentVictoriaQuestRuntimeCatalog.HuntMap(
                            Math.max(1, rank == Integer.MAX_VALUE ? 1 : rank), mapId,
                            Math.max(1, recommended), Math.max(Math.max(1, recommended), maximum),
                            List.copyOf(targetMobIds));
            return new DeficitChoice(map, evidence, Map.copyOf(evidenceByObjective),
                    Long.MAX_VALUE, 0L, 0L, 0);
        }
    }
}
