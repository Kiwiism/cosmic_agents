package server.agents.plans.mapleisland;

import server.agents.capabilities.objective.AgentObjectiveVariationRuntime;
import server.agents.plans.amherst.AmherstPlanCard;
import server.agents.plans.amherst.AmherstPlanObjective;
import server.agents.plans.amherst.AmherstPlanObjectiveKind;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;

/** Maple Island plan extension for dependency-safe Amherst quest-block ordering. */
final class MapleIslandAmherstQuestOrderPolicy {
    private static final String VARIATION_KEY = "maple-island-amherst-quest-block-order";

    private MapleIslandAmherstQuestOrderPolicy() {
    }

    static AmherstPlanCard apply(AmherstPlanCard card, AgentRuntimeEntry entry) {
        int variant = AgentObjectiveVariationRuntime.selectPlanVariant(
                entry, VARIATION_KEY, 2).orElse(0);
        if (variant == 0 || !"maple-island-full-mvp".equals(card.planId())) {
            return card;
        }

        List<AmherstPlanObjective> objectives = new ArrayList<>(card.objectives());
        int start = index(objectives, AmherstPlanObjectiveKind.QUEST_COMPLETE, 1037);
        List<AmherstPlanObjective> mariaAndLucas = List.of(
                require(objectives, start, AmherstPlanObjectiveKind.QUEST_COMPLETE, 1037),
                require(objectives, start + 1, AmherstPlanObjectiveKind.QUEST_CHAIN, 1038),
                require(objectives, start + 7, AmherstPlanObjectiveKind.QUEST_START, 1040));
        List<AmherstPlanObjective> pio = List.of(
                require(objectives, start + 2, AmherstPlanObjectiveKind.QUEST_START, 1008),
                require(objectives, start + 3, AmherstPlanObjectiveKind.REACTOR_HIT, 1008),
                require(objectives, start + 4, AmherstPlanObjectiveKind.REACTOR_BOX_ITEMS, 1008),
                require(objectives, start + 5, AmherstPlanObjectiveKind.QUEST_COMPLETE, 1008),
                require(objectives, start + 6, AmherstPlanObjectiveKind.QUEST_CHAIN, 1020));

        List<AmherstPlanObjective> varied = new ArrayList<>(pio.size() + mariaAndLucas.size());
        varied.addAll(pio);
        varied.addAll(mariaAndLucas);
        objectives.subList(start, start + varied.size()).clear();
        objectives.addAll(start, varied);
        return copyWithObjectives(card, objectives);
    }

    private static int index(List<AmherstPlanObjective> objectives,
                             AmherstPlanObjectiveKind kind,
                             int questId) {
        for (int index = 0; index < objectives.size(); index++) {
            if (matches(objectives.get(index), kind, questId)) {
                return index;
            }
        }
        throw new IllegalStateException("Maple Island Amherst quest block is incomplete");
    }

    private static AmherstPlanObjective require(List<AmherstPlanObjective> objectives,
                                                 int index,
                                                 AmherstPlanObjectiveKind kind,
                                                 int questId) {
        if (index < 0 || index >= objectives.size()
                || !matches(objectives.get(index), kind, questId)) {
            throw new IllegalStateException("Maple Island Amherst quest block order is invalid");
        }
        return objectives.get(index);
    }

    private static boolean matches(AmherstPlanObjective objective,
                                   AmherstPlanObjectiveKind kind,
                                   int questId) {
        return objective.kind() == kind && objective.allQuestIds().contains(questId);
    }

    private static AmherstPlanCard copyWithObjectives(AmherstPlanCard card,
                                                       List<AmherstPlanObjective> objectives) {
        return new AmherstPlanCard(card.schemaVersion(), card.planId(), card.title(), card.category(),
                card.priority(), card.status(), card.objectiveMode(), card.focusPolicy(),
                card.entryCriteria(), card.exitCriteria(), card.requiredQuestIds(),
                card.excludedQuestIds(), objectives);
    }
}
