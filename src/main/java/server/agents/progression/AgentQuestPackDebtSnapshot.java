package server.agents.progression;

import client.Character;
import client.QuestStatus;
import server.agents.integration.PrimitiveCapabilityGateway;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Live, quest-aware hunting debt for every active quest in one authored pack. */
final class AgentQuestPackDebtSnapshot {
    private final List<Debt> debts;
    private final Map<ConditionKey, Set<Integer>> objectiveQuestIds;
    private final Set<Integer> activeQuestIds;
    private final Set<Integer> completedQuestIds;

    private AgentQuestPackDebtSnapshot(
            List<Debt> debts,
            Map<ConditionKey, Set<Integer>> objectiveQuestIds,
            Set<Integer> activeQuestIds,
            Set<Integer> completedQuestIds) {
        this.debts = List.copyOf(debts);
        this.objectiveQuestIds = Map.copyOf(objectiveQuestIds);
        this.activeQuestIds = Set.copyOf(activeQuestIds);
        this.completedQuestIds = Set.copyOf(completedQuestIds);
    }

    static AgentQuestPackDebtSnapshot capture(
            AgentVictoriaSharedQuestPackCatalog.Pack pack,
            Character agent,
            PrimitiveCapabilityGateway gateway) {
        Set<Integer> packQuestIds = pack.steps().stream()
                .filter(step -> "QUEST".equals(step.type()) && step.questId() > 0)
                .map(AgentVictoriaSharedQuestPackCatalog.Step::questId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Integer> activeQuestIds = packQuestIds.stream()
                .filter(questId -> gateway.questStatus(agent, questId)
                        == QuestStatus.Status.STARTED.getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Integer> completedQuestIds = packQuestIds.stream()
                .filter(questId -> gateway.questStatus(agent, questId)
                        == QuestStatus.Status.COMPLETED.getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        AgentVictoriaQuestHuntIndexRepository repository =
                AgentVictoriaQuestHuntIndexRepository.defaultRepository();
        List<AgentVictoriaQuestHuntIndexRepository.ObjectiveReference> all =
                repository.findObjectivesForQuests(packQuestIds);
        Map<ConditionKey, Set<Integer>> objectiveQuestIds = new LinkedHashMap<>();
        for (AgentVictoriaQuestHuntIndexRepository.ObjectiveReference reference : all) {
            objectiveQuestIds.computeIfAbsent(conditionKey(reference), ignored -> new LinkedHashSet<>())
                    .add(reference.questId());
        }
        List<AgentVictoriaQuestHuntIndexRepository.ObjectiveReference> active = all.stream()
                .filter(reference -> activeQuestIds.contains(reference.questId()))
                .toList();

        List<Debt> debts = new ArrayList<>();
        Map<Integer, List<AgentVictoriaQuestHuntIndexRepository.ObjectiveReference>> collects =
                active.stream().filter(AgentQuestPackDebtSnapshot::collectObjective)
                        .collect(Collectors.groupingBy(
                                reference -> reference.objective().targetId(),
                                LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<Integer, List<AgentVictoriaQuestHuntIndexRepository.ObjectiveReference>>
                entry : collects.entrySet()) {
            List<AgentVictoriaQuestHuntIndexRepository.ObjectiveReference> references = entry.getValue();
            int required = references.stream()
                    .mapToInt(reference -> reference.objective().requiredCount()).sum();
            int current = gateway.itemCount(agent, entry.getKey());
            if (current < required) {
                Set<Integer> sources = references.stream()
                        .flatMap(reference -> sourceMobIds(reference.objective()).stream())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                int representativeQuestId = references.stream()
                        .mapToInt(AgentVictoriaQuestHuntIndexRepository.ObjectiveReference::questId)
                        .min().orElseThrow();
                AgentHuntSelectionRequest.ObjectiveDemand demand =
                        new AgentHuntSelectionRequest.ObjectiveDemand(
                                representativeQuestId, "pack:collect:" + entry.getKey(),
                                "collect-item", entry.getKey(), required, current, sources);
                debts.add(new Debt(demand, references));
            }
        }
        active.stream().filter(reference -> !collectObjective(reference)).forEach(reference -> {
            AgentVictoriaQuestHuntIndex.Objective objective = reference.objective();
            int current = gateway.questProgress(agent, reference.questId(), objective.targetId());
            if (current < objective.requiredCount()) {
                debts.add(new Debt(new AgentHuntSelectionRequest.ObjectiveDemand(
                        reference.questId(), objective.objectiveId(), objective.type(),
                        objective.targetId(), objective.requiredCount(), current,
                        sourceMobIds(objective)), List.of(reference)));
            }
        });
        debts.sort(Comparator.comparingInt(debt -> debt.demand().questId()));
        return new AgentQuestPackDebtSnapshot(
                debts, objectiveQuestIds, activeQuestIds, completedQuestIds);
    }

    List<AgentHuntSelectionRequest.ObjectiveDemand> demands() {
        return debts.stream().map(Debt::demand).toList();
    }

    int progressUnits() {
        return debts.stream().mapToInt(debt -> debt.demand().currentCount()).sum();
    }

    int remainingUnits() {
        return debts.stream().mapToInt(debt -> debt.demand().remainingCount()).sum();
    }

    Set<Integer> allSourceMobIds() {
        return debts.stream().flatMap(debt -> debt.demand().sourceMobIds().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    Set<Integer> targetMobIdsForMap(int mapId) {
        Set<Integer> result = new LinkedHashSet<>();
        for (Debt debt : debts) {
            boolean candidateFound = false;
            for (AgentVictoriaQuestHuntIndexRepository.ObjectiveReference reference
                    : debt.references()) {
                for (AgentVictoriaQuestHuntIndex.Candidate candidate
                        : reference.objective().candidates()) {
                    if (candidate.mapId() == mapId) {
                        result.addAll(candidate.targetMobIds());
                        candidateFound = true;
                    }
                }
            }
            if (!candidateFound) {
                result.addAll(debt.demand().sourceMobIds());
            }
        }
        return Set.copyOf(result);
    }

    boolean conditionsMet(
            Character agent,
            AgentVictoriaSharedQuestPackCatalog.Step step,
            PrimitiveCapabilityGateway gateway) {
        for (AgentVictoriaSharedQuestPackCatalog.Condition condition : step.conditions()) {
            ConditionKey key = conditionKey(condition);
            if (debts.stream().anyMatch(debt -> debt.matches(key))) {
                return false;
            }
            Set<Integer> owners = objectiveQuestIds.getOrDefault(key, Set.of());
            boolean relevantQuestKnown = owners.stream().anyMatch(
                    questId -> activeQuestIds.contains(questId)
                            || completedQuestIds.contains(questId));
            if (!relevantQuestKnown && !AgentVictoriaSharedQuestPackRuntime.conditionMet(
                    agent, condition, gateway)) {
                return false;
            }
        }
        return true;
    }

    String diagnosticSummary() {
        return debts.stream().map(debt -> debt.demand().objectiveId() + "="
                        + debt.demand().currentCount() + "/" + debt.demand().requiredCount())
                .reduce((left, right) -> left + "," + right).orElse("none");
    }

    String scopeSignature() {
        return debts.stream().map(debt -> debt.demand().objectiveId())
                .sorted().reduce((left, right) -> left + "|" + right).orElse("none");
    }

    private static boolean collectObjective(
            AgentVictoriaQuestHuntIndexRepository.ObjectiveReference reference) {
        return reference.objective().type().toLowerCase(Locale.ROOT).contains("collect");
    }

    private static Set<Integer> sourceMobIds(AgentVictoriaQuestHuntIndex.Objective objective) {
        if (!objective.sourceMobIds().isEmpty()) {
            return Set.copyOf(objective.sourceMobIds());
        }
        return objective.candidates().stream()
                .flatMap(candidate -> candidate.targetMobIds().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static ConditionKey conditionKey(
            AgentVictoriaQuestHuntIndexRepository.ObjectiveReference reference) {
        AgentVictoriaQuestHuntIndex.Objective objective = reference.objective();
        return new ConditionKey(collectObjective(reference) ? "ITEM" : "QUEST_KILL",
                collectObjective(reference) ? 0 : reference.questId(), objective.targetId());
    }

    private static ConditionKey conditionKey(
            AgentVictoriaSharedQuestPackCatalog.Condition condition) {
        return new ConditionKey(condition.type(),
                "ITEM".equals(condition.type()) ? 0 : condition.questId(),
                condition.targetId());
    }

    private record Debt(
            AgentHuntSelectionRequest.ObjectiveDemand demand,
            List<AgentVictoriaQuestHuntIndexRepository.ObjectiveReference> references) {
        private Debt {
            references = List.copyOf(references);
        }

        boolean matches(ConditionKey key) {
            if (demand.targetId() != key.targetId()) {
                return false;
            }
            if ("ITEM".equals(key.type())) {
                return demand.collectObjective();
            }
            return !demand.collectObjective() && demand.questId() == key.questId();
        }
    }

    private record ConditionKey(String type, int questId, int targetId) {
    }
}
